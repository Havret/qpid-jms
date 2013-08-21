/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.jms.impl;

import java.io.Serializable;
import java.util.UUID;

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

import org.apache.qpid.jms.engine.AmqpConnection;
import org.apache.qpid.jms.engine.AmqpReceiver;
import org.apache.qpid.jms.engine.AmqpSender;
import org.apache.qpid.jms.engine.AmqpSession;
import org.apache.qpid.jms.engine.ConnectionException;
import org.apache.qpid.jms.engine.LinkException;
import org.apache.qpid.proton.TimeoutException;

public class SessionImpl implements Session
{
    private AmqpSession _amqpSession;
    private ConnectionImpl _connectionImpl;

    public SessionImpl(AmqpSession amqpSession, ConnectionImpl connectionImpl)
    {
        _amqpSession = amqpSession;
        _connectionImpl = connectionImpl;
    }

    public void establish() throws TimeoutException, InterruptedException
    {
        _connectionImpl.waitUntil(new SimplePredicate("Session established", _amqpSession)
        {
            @Override
            public boolean test()
            {
                return _amqpSession.isEstablished();
            }
        }, AmqpConnection.TIMEOUT);
    }

    @Override
    public void close() throws JMSException
    {
        _connectionImpl.lock();
        try
        {
            _amqpSession.close();
            _connectionImpl.stateChanged();
            while(!_amqpSession.isClosed())
            {
                _connectionImpl.waitUntil(new SimplePredicate("Session is closed", _amqpSession)
                {
                    @Override
                    public boolean test()
                    {
                        return _amqpSession.isClosed();
                    }
                }, AmqpConnection.TIMEOUT);
            }

            if(_amqpSession.getSessionError().getCondition() != null)
            {
                throw new ConnectionException("Session close failed: " + _amqpSession.getSessionError());
            }
        }
        catch(InterruptedException e)
        {
            Thread.currentThread().interrupt();
            JMSException jmse = new JMSException("Interrupted while trying to close session");
            jmse.setLinkedException(e);
            throw jmse;
        }
        catch (TimeoutException | ConnectionException e)
        {
            JMSException jmse = new JMSException("Unable to close session");
            jmse.setLinkedException(e);
            throw jmse;
        }
        finally
        {
            _connectionImpl.releaseLock();
        }
    }

    ConnectionImpl getConnectionImpl()
    {
        return _connectionImpl;
    }


    @Override
    public MessageProducer createProducer(Destination destination) throws JMSException
    {
        if(destination == null)
        {
            throw new UnsupportedOperationException("Unspecified destinations are not yet supported");
        }
        else if (destination instanceof Queue)
        {
            Queue queue = (Queue) destination;
            String senderName = "producer-" + queue.getQueueName() + "-" + UUID.randomUUID();
            return createSender(senderName, queue.getQueueName());
        }
        else if(destination instanceof Topic)
        {
            throw new UnsupportedOperationException("Topics are not yet supported");
        }
        else
        {
            throw new IllegalArgumentException("Destination expected to be a Queue or a Topic but was: " + destination.getClass());
        }

    }

    private SenderImpl createSender(String senderName, String address) throws JMSException
    {
        _connectionImpl.lock();
        try
        {
            AmqpSender amqpSender = _amqpSession.createAmqpSender(address);
            SenderImpl sender = new SenderImpl(this, amqpSender);
            _connectionImpl.stateChanged();
            sender.establish();
            return sender;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            JMSException jmse = new JMSException("Interrupted while trying to create sender");
            jmse.setLinkedException(e);
            throw jmse;
        }
        catch (TimeoutException | LinkException e)
        {
            JMSException jmse = new JMSException("Unable to create sender: " + e.getMessage());
            jmse.setLinkedException(e);
            throw jmse;
        }
        finally
        {
            _connectionImpl.releaseLock();
        }
    }

    public ReceiverImpl createReceiver(String name, String address) throws TimeoutException, InterruptedException, LinkException
    {
        _connectionImpl.lock();
        try
        {
            AmqpReceiver amqpReceiver = _amqpSession.createAmqpReceiver(name, address);
            ReceiverImpl receiver = new ReceiverImpl(this, amqpReceiver);
            _connectionImpl.stateChanged();
            receiver.establish();
            return receiver;
        }
        finally
        {
            _connectionImpl.releaseLock();
        }
    }

    @Override
    public BytesMessage createBytesMessage() throws JMSException
    {
        // PHTODO Auto-generated method stub
        throw new UnsupportedOperationException("PHTODO");
    }

    @Override
    public MapMessage createMapMessage() throws JMSException
    {
        // PHTODO Auto-generated method stub
        throw new UnsupportedOperationException("PHTODO");
    }

    @Override
    public Message createMessage() throws JMSException
    {
        // PHTODO Auto-generated method stub
        throw new UnsupportedOperationException("PHTODO");
    }

    @Override
    public ObjectMessage createObjectMessage() throws JMSException
    {
        // PHTODO Auto-generated method stub
        throw new UnsupportedOperationException("PHTODO");
    }

    @Override
    public ObjectMessage createObjectMessage(Serializable object) throws JMSException
    {
        // PHTODO Auto-generated method stub
        throw new UnsupportedOperationException("PHTODO");
    }

    @Override
    public StreamMessage createStreamMessage() throws JMSException
    {
        // PHTODO Auto-generated method stub
        throw new UnsupportedOperationException("PHTODO");
    }

    @Override
    public TextMessage createTextMessage() throws JMSException
    {
        return new TextMessageImpl();
    }

    @Override
    public TextMessage createTextMessage(String text) throws JMSException
    {
        return new TextMessageImpl(text);
    }

    @Override
    public boolean getTransacted() throws JMSException
    {
        // PHTODO Auto-generated method stub
        throw new UnsupportedOperationException("PHTODO");
    }

    @Override
    public int getAcknowledgeMode() throws JMSException
    {
        // PHTODO Auto-generated method stub
        throw new UnsupportedOperationException("PHTODO");
    }

    @Override
    public void commit() throws JMSException
    {
        // PHTODO Auto-generated method stub
        throw new UnsupportedOperationException("PHTODO");
    }

    @Override
    public void rollback() throws JMSException
    {
        // PHTODO Auto-generated method stub
        throw new UnsupportedOperationException("PHTODO");
    }

    @Override
    public void recover() throws JMSException
    {
        // PHTODO Auto-generated method stub
        throw new UnsupportedOperationException("PHTODO");
    }

    @Override
    public MessageListener getMessageListener() throws JMSException
    {
        // PHTODO Auto-generated method stub
        throw new UnsupportedOperationException("PHTODO");
    }

    @Override
    public void setMessageListener(MessageListener listener) throws JMSException
    {
        // PHTODO Auto-generated method stub
        throw new UnsupportedOperationException("PHTODO");
    }

    @Override
    public void run()
    {
        // PHTODO Auto-generated method stub
        throw new UnsupportedOperationException("PHTODO");
    }

    @Override
    public MessageConsumer createConsumer(Destination destination) throws JMSException
    {
        // PHTODO Auto-generated method stub
        throw new UnsupportedOperationException("PHTODO");
    }

    @Override
    public MessageConsumer createConsumer(Destination destination, String messageSelector) throws JMSException
    {
        // PHTODO Auto-generated method stub
        throw new UnsupportedOperationException("PHTODO");
    }

    @Override
    public MessageConsumer createConsumer(Destination destination, String messageSelector, boolean NoLocal) throws JMSException
    {
        // PHTODO Auto-generated method stub
        throw new UnsupportedOperationException("PHTODO");
    }

    @Override
    public Queue createQueue(String queueName) throws JMSException
    {
        return new QueueImpl(queueName);
    }

    @Override
    public Topic createTopic(String topicName) throws JMSException
    {
        // PHTODO Auto-generated method stub
        throw new UnsupportedOperationException("PHTODO");
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic topic, String name) throws JMSException
    {
        // PHTODO Auto-generated method stub
        throw new UnsupportedOperationException("PHTODO");
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic topic, String name, String messageSelector, boolean noLocal) throws JMSException
    {
        // PHTODO Auto-generated method stub
        throw new UnsupportedOperationException("PHTODO");
    }

    @Override
    public QueueBrowser createBrowser(Queue queue) throws JMSException
    {
        // PHTODO Auto-generated method stub
        throw new UnsupportedOperationException("PHTODO");
    }

    @Override
    public QueueBrowser createBrowser(Queue queue, String messageSelector) throws JMSException
    {
        // PHTODO Auto-generated method stub
        throw new UnsupportedOperationException("PHTODO");
    }

    @Override
    public TemporaryQueue createTemporaryQueue() throws JMSException
    {
        // PHTODO Auto-generated method stub
        throw new UnsupportedOperationException("PHTODO");
    }

    @Override
    public TemporaryTopic createTemporaryTopic() throws JMSException
    {
        // PHTODO Auto-generated method stub
        throw new UnsupportedOperationException("PHTODO");
    }

    @Override
    public void unsubscribe(String name) throws JMSException
    {
        // PHTODO Auto-generated method stub
        throw new UnsupportedOperationException("PHTODO");
    }
}
