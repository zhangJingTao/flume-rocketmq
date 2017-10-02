/**
 * Copyright 2006-2014 handu.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.handu.flume.sink.rocketmq;

import java.util.List;

import com.alibaba.rocketmq.client.exception.MQClientException;
import com.alibaba.rocketmq.client.producer.MQProducer;
import com.alibaba.rocketmq.client.producer.MessageQueueSelector;
import com.alibaba.rocketmq.client.producer.SendResult;
import com.alibaba.rocketmq.common.message.Message;
import com.alibaba.rocketmq.common.message.MessageQueue;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

import org.apache.flume.*;
import org.apache.flume.conf.Configurable;
import org.apache.flume.sink.AbstractSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 发送消息
 *
 * @author Jinkai.Ma<majinkai@handu.com>
 * @since 2015-01-16
 */
public class RocketMQSink extends AbstractSink implements Configurable {

	private static final Logger LOG = LoggerFactory
			.getLogger(RocketMQSink.class);

	private String topic;
	private String tag;
	private MQProducer producer;
	private Integer queue;

	@Override
	public void configure(Context context) {
		// 获取配置项
		topic = context.getString(RocketMQSinkUtil.TOPIC_CONFIG,
				RocketMQSinkUtil.TOPIC_DEFAULT);
		tag = context.getString(RocketMQSinkUtil.TAG_CONFIG,
				RocketMQSinkUtil.TAG_DEFAULT);
		queue = context.getInteger(RocketMQSinkUtil.QUEUE_CONFIG,
				RocketMQSinkUtil.QUEUE_DEFAULT);
		// 初始化Producer
		producer = Preconditions.checkNotNull(RocketMQSinkUtil
				.getProducer(context));
	}

	@Override
	public Status process() throws EventDeliveryException {
		Channel channel = getChannel();
		Transaction tx = channel.getTransaction();
		try {
			tx.begin();
			Event event = channel.take();
			if (event == null || event.getBody() == null || event.getBody().length == 0) {
				tx.commit();
				return Status.READY;
			}
			// 发送消息
			SendResult sendResult = producer.send(new Message(topic, tag, event.getBody()),
					new MessageQueueSelector() {
						@Override
						public MessageQueue select(List<MessageQueue> mqs,Message msg, Object arg) {
							Integer id = (Integer) arg;  
	                        int index = id % mqs.size();  
	                        return mqs.get(index);  
						}
					}, queue);
			if (LOG.isDebugEnabled()) {
				LOG.debug("SendResult={}, Message={}", sendResult,event.getBody());
			}
			tx.commit();
			return Status.READY;
		} catch (Exception e) {
			LOG.error("RocketMQSink send message exception", e);
			try {
				tx.rollback();
				return Status.BACKOFF;
			} catch (Exception e2) {
				LOG.error("Rollback exception", e2);
			}
			return Status.BACKOFF;
		} finally {
			tx.close();
		}
	}

	@Override
	public synchronized void start() {
		try {
			// 启动Producer
			producer.start();
		} catch (MQClientException e) {
			LOG.error("RocketMQSink start producer failed", e);
			Throwables.propagate(e);
		}
		super.start();
	}
	
	@Override
	public synchronized void stop() {
		// 停止Producer
		producer.shutdown();
		super.stop();
	}
}
