package com.chg.yuaicodemother.constant;

public interface kafkaConstant {
    /**
     * kafka topic name（Java → Python 任务下发）
     */
    String TOPIC_NAME = "agent-generation-tasks";

    /**
     * Python AI 服务结果回传 topic
     */
    String TASK_RESULT_TOPIC = "task-result-topic";

    /**
     * 死信队列 topic（消费失败超过重试次数后进入）
     */
    String TASK_RESULT_DLT_TOPIC = "task-result-topic.DLT";

    /**
     * 消费者组
     */
    String CHAT_HISTORY_CONSUMER_GROUP = "chat-history-consumer-group";
}
