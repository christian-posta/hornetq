/*
 * Copyright 2005-2014 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.hornetq.jms.example;

import java.util.ArrayList;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.XAConnection;
import javax.jms.XAConnectionFactory;
import javax.jms.XASession;
import javax.naming.InitialContext;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.hornetq.common.example.DummyXid;
import org.hornetq.common.example.HornetQExample;
import org.hornetq.utils.UUIDGenerator;
import io.netty.util.CharsetUtil;

/**
 * A simple JMS example showing the usage of XA support in JMS.
 *
 * @author <a href="hgao@redhat.com">Howard Gao</a>
 */
public class XASendExample extends HornetQExample
{
   private volatile boolean result = true;

   private final ArrayList<String> receiveHolder = new ArrayList<String>();

   public static void main(final String[] args)
   {
      new XASendExample().run(args);
   }

   @Override
   public boolean runExample() throws Exception
   {
      XAConnection connection = null;
      InitialContext initialContext = null;
      try
      {
         // Step 1. Create an initial context to perform the JNDI lookup.
         initialContext = getContext(0);

         // Step 2. Lookup on the queue
         Queue queue = (Queue)initialContext.lookup("/queue/exampleQueue");

         // Step 3. Perform a lookup on the XA Connection Factory
         XAConnectionFactory cf = (XAConnectionFactory)initialContext.lookup("/XAConnectionFactory");

         // Step 4.Create a JMS XAConnection
         connection = cf.createXAConnection();

         // Step 5. Start the connection
         connection.start();

         // Step 6. Create a JMS XASession
         XASession xaSession = connection.createXASession();

         // Step 7. Create a normal session
         Session normalSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         // Step 8. Create a normal Message Consumer
         MessageConsumer normalConsumer = normalSession.createConsumer(queue);
         normalConsumer.setMessageListener(new SimpleMessageListener());

         // Step 9. Get the JMS Session
         Session session = xaSession.getSession();

         // Step 10. Create a message producer
         MessageProducer producer = session.createProducer(queue);

         // Step 11. Create two Text Messages
         TextMessage helloMessage = session.createTextMessage("hello");
         TextMessage worldMessage = session.createTextMessage("world");

         // Step 12. create a transaction
         Xid xid1 =
                  new DummyXid("xa-example1".getBytes(CharsetUtil.UTF_8), 1, UUIDGenerator.getInstance()
                                                                           .generateStringUUID()
                                                                           .getBytes());

         // Step 13. Get the JMS XAResource
         XAResource xaRes = xaSession.getXAResource();

         // Step 14. Begin the Transaction work
         xaRes.start(xid1, XAResource.TMNOFLAGS);

         // Step 15. do work, sending two messages.
         producer.send(helloMessage);
         producer.send(worldMessage);

         Thread.sleep(2000);

         // Step 16. Check the result, it should receive none!
         checkNoMessageReceived();

         // Step 17. Stop the work
         xaRes.end(xid1, XAResource.TMSUCCESS);

         // Step 18. Prepare
         xaRes.prepare(xid1);

         // Step 19. Roll back the transaction
         xaRes.rollback(xid1);

         // Step 20. No messages should be received!
         checkNoMessageReceived();

         // Step 21. Create another transaction
         Xid xid2 = new DummyXid("xa-example2".getBytes(), 1, UUIDGenerator.getInstance()
                                                                           .generateStringUUID()
                                                                           .getBytes());

         // Step 22. Start the transaction
         xaRes.start(xid2, XAResource.TMNOFLAGS);

         // Step 23. Re-send those messages
         producer.send(helloMessage);
         producer.send(worldMessage);

         // Step 24. Stop the work
         xaRes.end(xid2, XAResource.TMSUCCESS);

         // Step 25. Prepare
         xaRes.prepare(xid2);

         // Step 26. No messages should be received at this moment
         checkNoMessageReceived();

         // Step 27. Commit!
         xaRes.commit(xid2, false);

         Thread.sleep(2000);

         // Step 28. Check the result, all message received
         checkAllMessageReceived();

         return result;
      }
      finally
      {
         // Step 29. Be sure to close our JMS resources!
         if (initialContext != null)
         {
            initialContext.close();
         }
         if (connection != null)
         {
            connection.close();
         }
      }
   }

   private void checkAllMessageReceived()
   {
      if (receiveHolder.size() != 2)
      {
         System.out.println("Number of messages received not correct ! -- " + receiveHolder.size());
         result = false;
      }
      receiveHolder.clear();
   }

   private void checkNoMessageReceived()
   {
      if (receiveHolder.size() > 0)
      {
         System.out.println("Message received, wrong!");
         result = false;
      }
      receiveHolder.clear();
   }

   public class SimpleMessageListener implements MessageListener
   {
      public void onMessage(final Message message)
      {
         try
         {
            System.out.println("Message received: " + message);
            receiveHolder.add(((TextMessage)message).getText());
         }
         catch (JMSException e)
         {
            result = false;
            e.printStackTrace();
         }
      }

   }

}
