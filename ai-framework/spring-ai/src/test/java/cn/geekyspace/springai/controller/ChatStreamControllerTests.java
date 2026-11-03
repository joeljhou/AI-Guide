//package cn.geekyspace.springai.controller;
//
//import jakarta.servlet.AsyncEvent;
//import jakarta.servlet.AsyncListener;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.ai.chat.client.ChatClient;
//import org.springframework.ai.chat.messages.AssistantMessage;
//import org.springframework.ai.chat.model.ChatModel;
//import org.springframework.ai.chat.model.ChatResponse;
//import org.springframework.ai.chat.model.Generation;
//import org.springframework.ai.chat.prompt.ChatOptions;
//import org.springframework.ai.chat.prompt.Prompt;
//import org.springframework.mock.web.MockAsyncContext;
//import org.springframework.test.web.servlet.MockMvc;
//import org.springframework.test.web.servlet.MvcResult;
//import org.springframework.test.web.servlet.setup.MockMvcBuilders;
//import reactor.core.publisher.Flux;
//
//import java.nio.charset.StandardCharsets;
//import java.util.List;
//import java.util.concurrent.atomic.AtomicBoolean;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.argThat;
//import static org.mockito.Mockito.*;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
//class ChatStreamControllerTests {
//
//    private ChatModel model;
//    private MockMvc mvc;
//
//    @BeforeEach
//    void setUp() {
//        model = mock(ChatModel.class);
//        when(model.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
//        ChatStreamController controller = new ChatStreamController(ChatClient.builder(model));
//        mvc = MockMvcBuilders.standaloneSetup(controller).build();
//    }
//
//    @Test
//    void streamsTextChunksAndDone() throws Exception {
//        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(
//                response(" 你好\n"), response("<b>世界</b>")));
//
//        String body = stream("你好");
//
//        assertThat(body).containsSubsequence(
//                "data:{\"content\":\" 你好\\n\"}",
//                "data:{\"content\":\"<b>世界</b>\"}",
//                "event:done\ndata:[DONE]\n\n");
//        assertThat(body).doesNotContain("event:failure");
//        assertThat(body.split("event:done", -1)).hasSize(2);
//        verify(model).stream(argThat((Prompt prompt) -> prompt.getUserMessage().getText().equals("你好")));
//    }
//
//    @Test
//    void sendsFailureWithoutExposingUpstreamError() throws Exception {
//        when(model.stream(any(Prompt.class))).thenReturn(Flux.concat(
//                Flux.just(response("已生成")),
//                Flux.error(new IllegalStateException("private-upstream-error"))));
//
//        String body = stream("你好");
//
//        assertThat(body).contains("已生成", "event:failure", "生成失败");
//        assertThat(body).doesNotContain("event:done", "private-upstream-error");
//    }
//
//    @Test
//    void handlesSynchronousModelFailure() throws Exception {
//        when(model.stream(any(Prompt.class))).thenThrow(new IllegalStateException("private-setup-error"));
//
//        String body = stream("你好");
//
//        assertThat(body).contains("event:failure");
//        assertThat(body).doesNotContain("event:done", "private-setup-error");
//    }
//
//    @Test
//    void rejectsInvalidPromptsBeforeCallingModel() throws Exception {
//        mvc.perform(get("/api/chat/stream").param("prompt", "   "))
//                .andExpect(status().isBadRequest());
//        mvc.perform(get("/api/chat/stream").param("prompt", "a".repeat(501)))
//                .andExpect(status().isBadRequest());
//        mvc.perform(get("/api/chat/stream"))
//                .andExpect(status().isBadRequest());
//
//        verify(model, never()).stream(any(Prompt.class));
//    }
//
//    @Test
//    void cancelsModelWhenRequestTimesOut() throws Exception {
//        AtomicBoolean cancelled = new AtomicBoolean();
//        when(model.stream(any(Prompt.class))).thenReturn(
//                Flux.<ChatResponse>never().doOnCancel(() -> cancelled.set(true)));
//
//        MvcResult pending = mvc.perform(get("/api/chat/stream").param("prompt", "你好"))
//                .andExpect(request().asyncStarted())
//                .andReturn();
//        MockAsyncContext context = (MockAsyncContext) pending.getRequest().getAsyncContext();
//        for (AsyncListener listener : context.getListeners()) {
//            listener.onTimeout(new AsyncEvent(context));
//        }
//
//        assertThat(cancelled).isTrue();
//        mvc.perform(asyncDispatch(pending)).andExpect(status().isOk());
//    }
//
//    @Test
//    void cancelsModelWhenContainerReportsDisconnect() throws Exception {
//        AtomicBoolean cancelled = new AtomicBoolean();
//        when(model.stream(any(Prompt.class))).thenReturn(
//                Flux.<ChatResponse>never().doOnCancel(() -> cancelled.set(true)));
//
//        MvcResult pending = mvc.perform(get("/api/chat/stream").param("prompt", "你好"))
//                .andExpect(request().asyncStarted())
//                .andReturn();
//        MockAsyncContext context = (MockAsyncContext) pending.getRequest().getAsyncContext();
//        for (AsyncListener listener : context.getListeners()) {
//            listener.onError(new AsyncEvent(context, new java.io.IOException("client disconnected")));
//        }
//
//        assertThat(cancelled).isTrue();
//    }
//
//    private String stream(String prompt) throws Exception {
//        MvcResult pending = mvc.perform(get("/api/chat/stream").param("prompt", prompt))
//                .andExpect(request().asyncStarted())
//                .andReturn();
//        MvcResult completed = mvc.perform(asyncDispatch(pending))
//                .andExpect(status().isOk())
//                .andReturn();
//        assertThat(completed.getResponse().getContentType()).startsWith("text/event-stream");
//        return completed.getResponse().getContentAsString(StandardCharsets.UTF_8);
//    }
//
//    private ChatResponse response(String text) {
//        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
//    }
//}
