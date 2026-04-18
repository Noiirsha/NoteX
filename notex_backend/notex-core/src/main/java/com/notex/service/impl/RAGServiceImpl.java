package com.notex.service.impl;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.notex.domain.Response;
import com.notex.domain.dto.RAGRequestDTO;
import com.notex.domain.po.Note;
import com.notex.domain.vo.RAGResultVO;
import com.notex.service.IRAGService;
import com.notex.utils.UserIdContextHolder;
import com.notex.utils.VectorStoreUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.notex.context.RAGContext.*;

/**
 * RAG检索增强生成服务实现类
 * 实现笔记向量化、关键词检索和向量删除的核心逻辑
 */
@Service
@Slf4j
public class RAGServiceImpl implements IRAGService {

    @Resource
    private VectorStoreUtils vectorStoreUtils;

    @Resource
    private TokenCountEstimator tokenCountEstimator;

    @Resource(name = "myTokenTextSplitter")
    private TokenTextSplitter myTokenTextSplitter;

    /**
     * 笔记向量化
     * 将笔记内容转换为向量并存储到向量数据库中
     *
     */

    @Async
    @Override
    public void note2EmbeddingViaAsync(String uuid, Long userId) {
        note2Embedding(uuid, userId);
    }

    @Override
    public void note2Embedding(String noteUuid, Long userId) {

        // 1. 动态获取vectorStore对象
        VectorStore vectorStore = vectorStoreUtils.getVectorStore(userId);

        // 2. 获取对应的note
        Note note = Db.lambdaQuery(Note.class)
                .eq(Note::getUserId, userId)
                .eq(Note::getNoteUuid, noteUuid)
                .select(Note::getId, Note::getTitle, Note::getContent)
                .one();

        if (note == null) {
            return;
        }

        if (StrUtil.isBlank(note.getContent())) {
            // 内容为空 - 清空原有索引
            deleteOldVectorsByNoteUUID(vectorStore, noteUuid);
        }

        // 3. 笔记内容 -> Document
        Map<String, Object> metadata = Map.of(
                METADATA_USER_ID, userId,
                METADATA_NOTE_UUID, noteUuid,
                METADATA_NOTE_TITLE, note.getTitle()
        );

        // 根据长度决定是否分词
        List<Document> documents = splitIfNeeded(note.getContent(), metadata);

        // 4. 清空原有数据后存入向量数据库
        deleteOldVectorsByNoteUUID(vectorStore, noteUuid);

        // 安全检查: 查看是否是大于10段文本，如果是，则要批量发送至端点。国产大模型会限制Size为10左右 / pre request.
        if (documents.size() > 10) {
            log.warn("Split后的结果超过10个Chunk, 将执行批次发送...");
            List<List<Document>> partition = ListUtil.partition(documents, 10);
            int handledChunk = 0;
            for (List<Document> chunk : partition) {
                log.debug("第{}/{}批次",  handledChunk+1, partition.size());

                // 执行发送
                vectorStore.add(chunk);

                // 休眠100ms，防止过高请求
                try {
                    Thread.sleep(100);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                handledChunk++;
            }
            log.debug("完成!");
        } else {
            log.debug("Chunk数量未超过上限，直接发送");
            vectorStore.add(documents);
        }

        // 5. 更新Index状态
        Db.lambdaUpdate(Note.class)
                .eq(Note::getId, note.getId())
                .set(Note::getIsIndexed, true)
                .update();
    }

    /**
     * 关键词检索
     * 根据关键词在向量数据库中检索相关的笔记内容
     *
     * @param dto RAG请求DTO，包含搜索内容
     * @return 检索结果列表
     */
    @Override
    public Response<List<RAGResultVO>> queryByKeywords(RAGRequestDTO dto) {

        // 获取threadUserId
        Long userId = UserIdContextHolder.get();

        return Response.success(getRAGResults(dto, userId));
    }

    @Override
    public List<RAGResultVO> queryByKeywords(Long userId, RAGRequestDTO dto) {
        return getRAGResults(dto, userId);
    }

    private @NonNull List<RAGResultVO> getRAGResults(RAGRequestDTO dto, Long userId) {
        // 1. 动态获取vectorStore对象
        VectorStore vectorStore = vectorStoreUtils.getVectorStore(userId);

        // 2. 检查dto内检索内容是否为空
        String searchContent = dto.getSearchRequest().getSearchContent();
        if (StrUtil.isBlank(searchContent)) return List.of();

        // 3. 执行检索
        SearchRequest searchRequest = SearchRequest.builder()
                .query(searchContent)
                .topK(SEARCH_RESULT_TOP_K)
                .filterExpression(new Filter.Expression(
                        Filter.ExpressionType.EQ,
                        new Filter.Key(METADATA_USER_ID),
                        new Filter.Value(userId)
                ))
                .build();

        List<Document> documents = vectorStore.similaritySearch(searchRequest);
        List<RAGResultVO> resultVOList = new ArrayList<>();
        documents.forEach( document -> {
            if (document.getText() != null) {
                RAGResultVO ragResultVO = new RAGResultVO();

                // 获取metadata
                Map<String, Object> metadata = document.getMetadata();
                ragResultVO.setNoteTitle((String) metadata.get(METADATA_NOTE_TITLE))
                           .setNoteUuid((String) metadata.get(METADATA_NOTE_UUID));

                // 获取返回内容
                ragResultVO.setContent(document.getText());

                // 获取命中率
                ragResultVO.setContentMatchRate(document.getScore());

                resultVOList.add(ragResultVO);
            }
        });

        return resultVOList;
    }



    /**
     * 删除笔记向量
     * 从向量数据库中删除指定笔记的向量数据
     *
     * @param dto RAG请求DTO，包含笔记UUID
     */
    @Override
    public void deleteNoteVector(RAGRequestDTO dto) {

        // 1. 动态获取vectorStore对象
        VectorStore vectorStore = vectorStoreUtils.getVectorStore();

        // 2. 检查dto内笔记内容是否为空
        RAGRequestDTO.NoteDetail note = dto.getNoteDetail();
        if (note == null || StrUtil.isBlank(note.getNoteUuid())) return;

        // 3. 执行删除
        deleteOldVectorsByNoteUUID(vectorStore, note.getNoteUuid());

    }

    @Override
    public void deleteUserVector() {

        // 获取线程UserId
        Long threadUserId = UserIdContextHolder.get();

        // 获取Vector容器
        VectorStore vectorStore = vectorStoreUtils.getVectorStore();

        // 通过Filter删除用户向量
        deleteOldVectorsByUserId(vectorStore, threadUserId);

        // 设置用户所有索引为未索引状态
        Db.lambdaUpdate(Note.class)
                .eq(Note::getUserId, threadUserId)
                .set(Note::getIsIndexed, false)
                .update();
    }

    /**
     * 根据需要切分文本
     * 将Markdown内容解析为Document，并在需要时进行切分
     *
     * @param content 笔记内容
     * @param metadata 元数据
     * @return 切分后的Document列表
     */
    private List<Document> splitIfNeeded(String content, Map<String, Object> metadata) {
        // 计算Token
        int tokenCount = tokenCountEstimator.estimate(content);
        log.debug("RAG::Doc2Emb::文档Token大小: {}", tokenCount);

        // Markdown转Document
        org.springframework.core.io.Resource mdResource = new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
        MarkdownDocumentReaderConfig mdReaderConfig = MarkdownDocumentReaderConfig.builder()
                .withHorizontalRuleCreateDocument(true)
                .withIncludeCodeBlock(true)
                .withIncludeBlockquote(true)
                .withAdditionalMetadata(metadata)
                .build();

        List<Document> mdDocument = new MarkdownDocumentReader(mdResource, mdReaderConfig).get();

        // 超过阈值，使用 Spring AI 的 TokenTextSplitter
        TokenTextSplitter splitter = myTokenTextSplitter;
        List<Document> split = splitter.split(mdDocument);
        log.debug("RAG::Doc2Emb::SplitCompleted. Chunks: {}", split.size());
        return split;
    }

    /**
     * 清空Vector内旧数据
     * 根据笔记UUID删除对应的向量数据
     *
     * @param vectorStore 向量存储对象
     * @param noteUuid 笔记UUID
     */
    // 清空Vector内旧数据
    private void deleteOldVectorsByNoteUUID(VectorStore vectorStore, String noteUuid) {
        Filter.Expression filter = new Filter.Expression(
                Filter.ExpressionType.EQ,
                new Filter.Key(METADATA_NOTE_UUID),
                new Filter.Value(noteUuid)
        );

        vectorStore.delete(filter);
    }

    private void deleteOldVectorsByUserId(VectorStore vectorStore, Long userId) {
        Filter.Expression filter = new Filter.Expression(
                Filter.ExpressionType.EQ,
                new Filter.Key(METADATA_USER_ID),
                new Filter.Value(userId)
        );

        vectorStore.delete(filter);
    }
}
