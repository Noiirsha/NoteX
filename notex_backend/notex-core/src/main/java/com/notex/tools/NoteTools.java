package com.notex.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.notex.context.NoteGroupContext;
import com.notex.domain.dto.RAGRequestDTO;
import com.notex.domain.po.Note;
import com.notex.service.INoteService;
import com.notex.service.IRAGService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.notex.context.AiContext.*;

@Component
@Slf4j
public class NoteTools {

    @Resource
    private IRAGService ragService;

    @Resource
    private INoteService noteService;

    @Tool(description = "获取页面下当前笔记内容", returnDirect = false)
    public String getCurrentPageNoteContent(ToolContext toolContext) {

        // 获取当前笔记内容
        Map<String, Object> context = toolContext.getContext();
        String noteContent = (String) context.get(TOOL_CONTEXT_NOTE_CONTENT);

        if (noteContent == null) {
            log.debug("ToolCalling::getCurrentPageNoteContent::笔记内容为null");
            return "请先打开对应的笔记页面才能读取页面下的笔记内容哦";
        }

        if (StrUtil.isEmpty(noteContent)) {
            log.debug("ToolCalling::getCurrentPageNoteContent::笔记内容为空");
            return "*当前笔记是空笔记*";
        }

        return noteContent;
    }

    @Tool(description = "根据笔记UUID获取目标笔记内容", returnDirect = false)
    public String getNoteByUUID(
            @ToolParam(description = "笔记UUID") String uuid,
            ToolContext toolContext
    ) {

        // 获取用户ID
        Map<String, Object> context = toolContext.getContext();
        Long userId = (Long) context.get(TOOL_CONTEXT_USER_ID);

        // 获取笔记
        Note note = noteService.lambdaQuery()
                .eq(Note::getUserId, userId)
                .eq(Note::getNoteUuid, uuid)
                .one();

        if (note == null) {
            log.debug("ToolCalling::getNoteByUUID::笔记内容为null");
            return "*不存在该笔记*";
        }

        return note.getContent();
    }

    @Tool(description = "根据关键词搜索笔记内容", returnDirect = false)
    public String searchByKeyWord(
            @ToolParam(description = "搜索关键词") String keywords,
            ToolContext toolContext
    ) {

        // 获取用户ID
        Map<String, Object> context = toolContext.getContext();
        Long userId = (Long) context.get(TOOL_CONTEXT_USER_ID);

        // 构建RAG请求
        try {

            // 构建请求DTO
            RAGRequestDTO dto = new RAGRequestDTO();
            RAGRequestDTO.SearchRequest searchRequest = new RAGRequestDTO.SearchRequest();
            searchRequest.setSearchContent(keywords);
            dto.setSearchRequest(searchRequest);

            // 交由RAG部分处理
            return JSONUtil.toJsonStr(ragService.queryByKeywords(userId, dto));

        } catch (Exception e) {
            log.error("ToolCalling::searchByKeyWord::{}", e.getMessage());
            return "搜索服务暂时不可用，请稍后再试或检查您的模型配置。";
        }

    }

    @Tool(description = "在当前文件夹下创建新笔记", returnDirect = false)
    public String createNote(
            @ToolParam(description = "新笔记的内容") String content,
            @ToolParam(description = "新笔记的标题") String title,
            ToolContext toolContext
    ) {
        // 获取当前笔记组以及用户ID
        Map<String, Object> context = toolContext.getContext();
        Long userId = (Long) context.get(TOOL_CONTEXT_USER_ID);
        Long groupId = (Long) context.get(TOOL_CONTEXT_GROUP_ID);

        if (groupId == null || groupId.equals(NoteGroupContext.ROOT_GROUP)) {
            log.debug("ToolCalling::createNote::空Workspace");
            return "请先选择一个工作区后，再尝试创建笔记哦";
        }

        // 存入笔记
        try {
            // 无效化缓存并创建笔记
            noteService.createNote(userId, groupId, title, content);
            return "笔记创建完成\n";
        } catch (Exception e) {
            log.error("ToolCalling::createNote::{}",e.getMessage());
            return "创建笔记时失败，请稍后重试";
        }

    }


}
