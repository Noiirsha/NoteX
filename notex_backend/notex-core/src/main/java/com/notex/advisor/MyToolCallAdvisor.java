package com.notex.advisor;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.model.tool.internal.ToolCallReactiveContextHolder;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class MyToolCallAdvisor extends ToolCallAdvisor {

    private boolean streamToolCallResponses = false;

    public MyToolCallAdvisor(ToolCallingManager toolCallingManager, int advisorOrder) {
        super(toolCallingManager, advisorOrder);
    }

    protected MyToolCallAdvisor(ToolCallingManager toolCallingManager, int advisorOrder, boolean conversationHistoryEnabled) {
        super(toolCallingManager, advisorOrder, conversationHistoryEnabled);
    }

    protected MyToolCallAdvisor(ToolCallingManager toolCallingManager, int advisorOrder, boolean conversationHistoryEnabled, boolean streamToolCallResponses) {
        super(toolCallingManager, advisorOrder, conversationHistoryEnabled, streamToolCallResponses);
        this.streamToolCallResponses = streamToolCallResponses;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        Assert.notNull(streamAdvisorChain, "streamAdvisorChain must not be null");
        Assert.notNull(chatClientRequest, "chatClientRequest must not be null");
        if (chatClientRequest.prompt().getOptions() != null && chatClientRequest.prompt().getOptions() instanceof ToolCallingChatOptions) {
            ChatClientRequest initializedRequest = this.doInitializeLoopStream(chatClientRequest, streamAdvisorChain);
            ToolCallingChatOptions optionsCopy = (ToolCallingChatOptions)chatClientRequest.prompt().getOptions().copy();
            optionsCopy.setInternalToolExecutionEnabled(false);
            return this.internalStream(streamAdvisorChain, initializedRequest, optionsCopy, initializedRequest.prompt().getInstructions());
        } else {
            throw new IllegalArgumentException("ToolCall Advisor requires ToolCallingChatOptions to be set in the ChatClientRequest options.");
        }
    }

    private Flux<ChatClientResponse> internalStream(StreamAdvisorChain streamAdvisorChain, ChatClientRequest originalRequest, ToolCallingChatOptions optionsCopy, List<Message> instructions) {
        return Flux.deferContextual((contextView) -> {
            ChatClientRequest processedRequest = ChatClientRequest.builder().prompt(new Prompt(instructions, optionsCopy)).context(originalRequest.context()).build();
            processedRequest = this.doBeforeStream(processedRequest, streamAdvisorChain);
            StreamAdvisorChain chainCopy = streamAdvisorChain.copy(this);
            Flux<ChatClientResponse> responseFlux = chainCopy.nextStream(processedRequest);
            AtomicReference<ChatClientResponse> aggregatedResponseRef = new AtomicReference();
            return this.streamWithToolCallResponses(responseFlux, aggregatedResponseRef, processedRequest, streamAdvisorChain, originalRequest, optionsCopy);
        });
    }

    private Flux<ChatClientResponse> streamWithToolCallResponses(Flux<ChatClientResponse> responseFlux, AtomicReference<ChatClientResponse> aggregatedResponseRef, ChatClientRequest finalRequest, StreamAdvisorChain streamAdvisorChain, ChatClientRequest originalRequest, ToolCallingChatOptions optionsCopy) {
        return responseFlux.publish((shared) -> {
            ChatClientMessageAggregator var10000 = new ChatClientMessageAggregator();
            Objects.requireNonNull(aggregatedResponseRef);
            Flux<ChatClientResponse> streamingBranch = var10000.aggregateChatClientResponse(shared, aggregatedResponseRef::set);
            Flux<ChatClientResponse> recursionBranch = Flux.defer(() -> this.handleToolCallRecursion((ChatClientResponse)aggregatedResponseRef.get(), finalRequest, streamAdvisorChain, originalRequest, optionsCopy));
            return streamingBranch.concatWith(recursionBranch);
        }).filter((ccr) -> this.streamToolCallResponses || ccr.chatResponse() == null || !ccr.chatResponse().hasToolCalls());
    }

    private Flux<ChatClientResponse> handleToolCallRecursion(ChatClientResponse aggregatedResponse, ChatClientRequest finalRequest, StreamAdvisorChain streamAdvisorChain, ChatClientRequest originalRequest, ToolCallingChatOptions optionsCopy) {
        if (aggregatedResponse == null) {
            return Flux.empty();
        } else {

            // 空信号占位符
            Flux<ChatClientResponse> signalFlux = Flux.empty();

            // 检查是否有tool calling
            if (checkIfHasToolCalling(aggregatedResponse) && aggregatedResponse.chatResponse() != null) {

                // 构建Generation
                List<Generation> generations = new ArrayList<>();

                aggregatedResponse.chatResponse().getResults().forEach(result -> {
                    result.getOutput().getToolCalls().forEach(toolCall -> {
                        // 封装信息到Generation对象
                        log.debug("Tool Calling触发。功能: {}, 参数: {}", toolCall.name(), toolCall.arguments());
                        String argumentsString = StrUtil.truncateUtf8(toolCall.arguments(), 30);
                        if (argumentsString.endsWith("...")) {
                            argumentsString += "}";
                        }
                        String signalText = String.format("<notex_tool_calling function=\"%s\" arguments=\"%s\" />\n"
                                , toolCall.name()
                                , argumentsString);
                        generations.add(new Generation(new AssistantMessage(signalText)));
                    });
                });

                // 伪造ChatClientResponse
                ChatResponse signalChatResp = ChatResponse.builder()
                        .generations(generations)
                        .build();

                ChatClientResponse signalResponseWrapper = ChatClientResponse.builder()
                        .chatResponse(signalChatResp)
                        .build();

                // 转换为立即发射的Flux流
                signalFlux = Flux.just(signalResponseWrapper);

            }

            // 继续执行接下来的请求
            aggregatedResponse = this.doAfterStream(aggregatedResponse, streamAdvisorChain);
            ChatResponse chatResponse = aggregatedResponse.chatResponse();
            boolean isToolCall = chatResponse != null && chatResponse.hasToolCalls();
            if (!isToolCall) {
                return this.doFinalizeLoopStream(Flux.empty(), streamAdvisorChain);
            } else {
                Assert.notNull(chatResponse, "redundant check that should never fail, but here to help NullAway");
                ChatClientResponse finalAggregatedResponse = aggregatedResponse;
                Flux<ChatClientResponse> toolCallFlux = Flux.deferContextual((ctx) -> {
                    ToolExecutionResult toolExecutionResult;
                    try {
                        ToolCallReactiveContextHolder.setContext(ctx);
                        toolExecutionResult = this.toolCallingManager.executeToolCalls(finalRequest.prompt(), chatResponse);
                    } finally {
                        ToolCallReactiveContextHolder.clearContext();
                    }

                    if (toolExecutionResult.returnDirect()) {
                        return Flux.just(finalAggregatedResponse.mutate().chatResponse(ChatResponse.builder().from(chatResponse).generations(ToolExecutionResult.buildGenerations(toolExecutionResult)).build()).build());
                    } else {
                        List<Message> nextInstructions = this.doGetNextInstructionsForToolCallStream(finalRequest, finalAggregatedResponse, toolExecutionResult);
                        return this.internalStream(streamAdvisorChain, originalRequest, optionsCopy, nextInstructions);
                    }
                });

                // 执行流篡改
                // return toolCallFlux.subscribeOn(Schedulers.boundedElastic());
                return signalFlux.concatWith(toolCallFlux.subscribeOn(Schedulers.boundedElastic()));
            }
        }
    }

    private boolean checkIfHasToolCalling(ChatClientResponse aggregatedResponse){
        ChatResponse resp = aggregatedResponse.chatResponse();
        return resp != null && resp.hasToolCalls();
    }


}
