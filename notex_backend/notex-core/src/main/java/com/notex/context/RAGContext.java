package com.notex.context;

/**
 * RAG模块上下文常量
 * 定义检索增强生成相关的配置参数和元数据键
 */
public class RAGContext {

    /*
                通用配置
     */

    /**
     * 最大VectorStores存储对象
     */
    public static final int MAX_STORAGE_VECTOR_STORES = 1000;

    /**
     * RAG文本切分: 最大单块Token Size
     */
    public static final int MAX_CHUNK_SIZE = 800;

    /**
     * RAG检索: 最大返回结果
     */
    public static final int SEARCH_RESULT_TOP_K = 20;

    /*
                Metadata
     */
    /**
     * 元数据键：用户ID
     */
    public static final String METADATA_USER_ID = "user_id";

    /**
     * 元数据键：笔记UUID
     */
    public static final String METADATA_NOTE_UUID = "note_uuid";

    /**
     * 元数据键：笔记标题
     */
    public static final String METADATA_NOTE_TITLE = "note_title";

}
