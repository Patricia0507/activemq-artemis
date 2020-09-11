/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.jms.example;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import javax.jms.Session;
import javax.security.auth.Subject;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.security.impl.SecurityStoreImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.impl.ServerSessionImpl;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.server.plugin.impl.LoggingActiveMQServerPluginLogger;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.protocol.SessionCallback;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager5;
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;

public class BrokerAuthPlugin implements ActiveMQServerPlugin {

  private final AtomicReference<ActiveMQServer> server = new AtomicReference<>();
  Map<String, Subject> subjectCache = new ConcurrentHashMap<>();

  @Override
  public void registered(ActiveMQServer server) {
    this.server.set(server);
  }

  /**
   * Before a message is delivered to a client consumer
   *
   * @param consumer  the consumer the message will be delivered to
   * @param reference message reference
   * @throws ActiveMQException
   */
  @Override
  public void beforeDeliver(ServerConsumer consumer, MessageReference reference)
      throws ActiveMQException {
    Subject subject = subjectCache.get(consumer.getConnectionID().toString());
    String requiredRole = reference.getMessage().getStringProperty("requiredRole");
    if (subject != null && requiredRole != null) {
      boolean permitted = new RolePrincipal(requiredRole).implies(subject);
      System.out.println("PLUGIN connectionID:"+consumer.getConnectionID()+" authorized:"+ permitted);
      reference.getMessage().setBrokerProperty(new SimpleString(consumer.getConnectionID().toString()),permitted);

      //TODO this could possibly a better way to do it but the check is performed prior to this method call and it only works when using AMQP
      if(!permitted)
      {
        reference.getMessage().rejectConsumer(consumer.getID());
      }
    }
  }

  /**
   * After a session has been created.
   *
   * @param session The newly created session
   * @throws ActiveMQException
   */
  @Override
  public void afterCreateSession(ServerSession session) throws ActiveMQException {
    ActiveMQSecurityManager securityManager = server.get().getSecurityManager();
    if(securityManager instanceof ActiveMQSecurityManager5 && session.getConnectionID()!=null)
    {

    Subject subject = ((ActiveMQSecurityManager5) securityManager).authenticate(session.getUsername(), session.getPassword(),
        session.getRemotingConnection(),session.getSecurityDomain());
      subjectCache.put(session.getConnectionID().toString(), subject);
    }
  }

  /**
   * A connection has been destroyed.
   *
   * @param connection
   * @throws ActiveMQException
   */
  @Override
  public void afterDestroyConnection(RemotingConnection connection) throws ActiveMQException {
    subjectCache.remove(connection.getID());
  }

}
