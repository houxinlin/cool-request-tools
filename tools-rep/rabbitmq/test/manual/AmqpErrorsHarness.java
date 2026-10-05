import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import dev.coolrequest.tool.rabbitmq.AmqpErrors;
import dev.coolrequest.tool.rabbitmq.RabbitClient;
import dev.coolrequest.tool.rabbitmq.RabbitConnection;

/**
 * 无 IDE 依赖的链路自测：逐场景连真实 broker，打印原始异常消息与 AmqpErrors 翻译结果。
 * 用法：java AmqpErrorsHarness <host> <port> <vhost> <user> <pass>
 */
public class AmqpErrorsHarness {

    public static void main(String[] args) throws Exception {
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String vhost = args[2];
        String user = args[3];
        String pass = args[4];

        RabbitConnection ok = new RabbitConnection("ok", host, port, vhost, user, pass);

        // 0. 正向对照：正确凭据 + passive 声明已存在队列 + basicConsume 立即取消
        scenario("正向对照(正确凭据连接+订阅)", () -> {
            Connection c = RabbitClient.factory(ok).newConnection();
            Channel ch = c.createChannel();
            ch.queueDeclarePassive("cr.ui.q.f");
            String tag = ch.basicConsume("cr.ui.q.f", false, "harness-" + System.currentTimeMillis(), false, false, null,
                    new com.rabbitmq.client.DefaultConsumer(ch) {
                        @Override
                        public void handleDelivery(String t, com.rabbitmq.client.Envelope e,
                                                   com.rabbitmq.client.AMQP.BasicProperties p, byte[] b) {
                        }
                    });
            ch.basicCancel(tag);
            ch.close();
            c.close(1000);
            return "OK: 连接/声明/订阅/取消全通";
        });

        // 1. 错误密码
        RabbitConnection badPass = new RabbitConnection("badpass", host, port, vhost, user, "wrong-password");
        scenario("错误密码", () -> {
            Connection c = RabbitClient.factory(badPass).newConnection();
            c.close(1000);
            return "(未抛异常?)";
        });

        // 2. 错误端口(5672 默认口)
        RabbitConnection badPort = new RabbitConnection("badport", host, 5672, vhost, user, pass);
        scenario("错误端口(5672)", () -> {
            Connection c = RabbitClient.factory(badPort).newConnection();
            c.close(1000);
            return "(未抛异常?)";
        });

        // 3. 错误 vhost
        RabbitConnection badVhost = new RabbitConnection("badvhost", host, port, "no_such_vhost", user, pass);
        scenario("错误 vhost", () -> {
            Connection c = RabbitClient.factory(badVhost).newConnection();
            c.close(1000);
            return "(未抛异常?)";
        });

        // 4. passive 声明不存在的队列(对应 Auto-declare 未勾选)
        scenario("passive声明不存在的队列", () -> {
            Connection c = RabbitClient.factory(ok).newConnection();
            try {
                Channel ch = c.createChannel();
                ch.queueDeclarePassive("cr.ui.q.nonexistent");
                return "(未抛异常?)";
            } finally {
                c.close(1000);
            }
        });

        // 5. 发布流程镜像 ProducerPanel: 先 exchangeDeclare(durable=false,autoDelete=true) 再 basicPublish 再 close
        scenario("发布到不存在的交换机(镜像Producer:先声明)", () -> {
            Connection c = RabbitClient.factory(ok).newConnection();
            try {
                Channel ch = c.createChannel();
                ch.exchangeDeclare("cr.ui.ex.nonexistent", com.rabbitmq.client.BuiltinExchangeType.DIRECT, false, true, null);
                ch.basicPublish("cr.ui.ex.nonexistent", "uik", null, "x".getBytes("UTF-8"));
                ch.close();
                return "OK: Producer 会先自动声明交换机, 因此该名字会被创建而非报错(走查发现)";
            } finally {
                c.close(1000);
            }
        });

        // 5b. 对已存在但属性不同的交换机按 Producer 口径再声明 → 应触发 406
        scenario("Producer口径声明与已存在属性冲突的交换机", () -> {
            Connection c = RabbitClient.factory(ok).newConnection();
            try {
                Channel ch = c.createChannel();
                ch.exchangeDeclare("cr.ui.ex.conflict", com.rabbitmq.client.BuiltinExchangeType.DIRECT, true, false, null);
                ch.exchangeDeclare("cr.ui.ex.conflict", com.rabbitmq.client.BuiltinExchangeType.DIRECT, false, true, null);
                return "(未抛异常?)";
            } finally {
                c.close(1000);
            }
        });

        // 6. durable 不匹配: cr.ui.q.d 是管理API默认(durable=false)建的, 再按 durable=true 声明
        scenario("durable参数冲突声明", () -> {
            Connection c = RabbitClient.factory(ok).newConnection();
            try {
                Channel ch = c.createChannel();
                ch.queueDeclare("cr.ui.q.d", true, false, false, null);
                return "(未抛异常?)";
            } finally {
                c.close(1000);
            }
        });

        // 7/8/9. mandatory+ReturnListener 不可路由检测(镜像 Producer 新逻辑)
        scenario("headers交换机: 无header发送(预期被退回 NO_ROUTE)", () -> {
            return publishAndCheckReturn(ok, "cr.ui.ex.headers", "", null, "ui-no-header");
        });
        scenario("headers交换机: header不匹配(预期被退回 NO_ROUTE)", () -> {
            return publishAndCheckReturn(ok, "cr.ui.ex.headers", "", java.util.Collections.singletonMap("x", "y"), "ui-wrong-header");
        });
        scenario("headers交换机: header匹配foo=bar(预期已路由)", () -> {
            return publishAndCheckReturn(ok, "cr.ui.ex.headers", "", java.util.Collections.singletonMap("foo", "bar"), "ui-match-header");
        });
        scenario("direct交换机: 正确routing key(预期已路由)", () -> {
            return publishAndCheckReturn(ok, "cr.ui.ex.direct", "uik", null, "ui-direct-ok");
        });

        // 13. AUTO 模式镜像：不声明直接发既有 durable 交换机（新默认行为，绝不自动创建、不会 406）
        //     自建自清该 durable 交换机，避免依赖服务端种子（种子被清理后此场景会假失败）
        scenario("AUTO模式: 不声明直接发既有durable交换机", () -> {
            Connection c = RabbitClient.factory(ok).newConnection();
            try {
                Channel setup = c.createChannel();
                setup.exchangeDeclare("cr.ui.ex.autodurable", com.rabbitmq.client.BuiltinExchangeType.DIRECT, true, false, null);
                setup.close();
                Channel ch = c.createChannel(); // 新通道：发布链路里没有任何声明动作
                java.util.List<com.rabbitmq.client.Return> returned = new java.util.concurrent.CopyOnWriteArrayList<>();
                ch.addReturnListener(returned::add);
                ch.basicPublish("cr.ui.ex.autodurable", "no-match-key", true,
                        new com.rabbitmq.client.AMQP.BasicProperties.Builder().deliveryMode(2).build(),
                        "ui-auto-direct".getBytes("UTF-8"));
                Thread.sleep(800);
                ch.close();
                Channel cleanup = c.createChannel();
                cleanup.exchangeDelete("cr.ui.ex.autodurable");
                cleanup.close();
                // 该交换机无绑定, 无论退回与否, 都证明 406 未发生(没有声明动作)
                return "OK: 发送链路无声明动作, 406 不可能发生 (退回数=" + returned.size() + " 属正常路由语义)";
            } finally {
                c.close(1000);
            }
        });

        // 14. AUTO 模式发不存在的交换机 → 404(不自动创建的正确语义)
        scenario("AUTO模式: 发不存在交换机(预期404不创建)", () -> {
            Connection c = RabbitClient.factory(ok).newConnection();
            try {
                Channel ch = c.createChannel();
                ch.basicPublish("cr.ui.ex.autononexist", "uik", true, null, "x".getBytes("UTF-8"));
                Thread.sleep(800);
                ch.close();
                return "(未抛异常?)";
            } finally {
                c.close(1000);
            }
        });

        // ===== Ack 确认链路（镜像 ConsumerPanel 手动/自动确认逻辑）=====
        final String ackQueue = "cr.ui.q.ack";
        scenario("手动Ack: basicAck 后消息不再回到队列", () -> {
            try (Ctx ctx = Ctx.open(ok, ackQueue, 3)) {
                long tag = ctx.consumeOne(false, 1);   // QoS=1：1 条在途 + 2 条 ready
                int inFlight = ctx.ready();
                ctx.ch.basicAck(tag, false);
                ctx.cycleChannel();                     // 关通道强制回投未确认消息，再读即时深度
                return "在途时 ready=" + inFlight + "(预期 2) → ack 后关通道重读 ready="
                        + ctx.ready() + "(预期 2：被 ack 的那条不再回来)";
            }
        });
        scenario("手动未Ack对照: 关通道后消息回队列(不丢)", () -> {
            try (Ctx ctx = Ctx.open(ok, ackQueue, 3)) {
                ctx.consumeOne(false, 1);               // 收下但不 ack（旧版行为）
                ctx.cycleChannel();
                return "关通道重读 ready=" + ctx.ready() + "(预期 3：未确认消息由 broker 全部重投)";
            }
        });
        scenario("手动Requeue: basicNack(requeue=true) 消息回队列", () -> {
            try (Ctx ctx = Ctx.open(ok, ackQueue, 1)) {
                long tag = ctx.consumeOne(false, 1);
                int inFlight = ctx.ready();
                ctx.ch.basicNack(tag, false, true);
                Thread.sleep(500);
                int back = ctx.ready();
                boolean redelivered = ctx.consumeOneRedelivered(false);
                return "在途时 ready=" + inFlight + "(预期 0) → requeue 后 ready=" + back
                        + "(预期 1 回到队列) 再投 redelivered=" + redelivered + "(预期 true)";
            }
        });
        scenario("手动Reject: basicNack(requeue=false) 消息丢弃", () -> {
            try (Ctx ctx = Ctx.open(ok, ackQueue, 1)) {
                long tag = ctx.consumeOne(false, 1);
                ctx.ch.basicNack(tag, false, false);
                Thread.sleep(500);
                return "ready=" + ctx.ready() + " (预期 0，消息已丢弃不回队列)";
            }
        });
        scenario("Auto ack: 订阅即确认(队列清空)", () -> {
            try (Ctx ctx = Ctx.open(ok, ackQueue, 2)) {
                boolean got = ctx.consumeAll(true, 2);
                Thread.sleep(500);
                return "收齐=" + got + " ready=" + ctx.ready() + " (预期 true + 0)";
            }
        });
        scenario("Prefetch 语义: 未确认达上限则停投(旧版无 ack 入口时的卡住现象)", () -> {
            try (Ctx ctx = Ctx.open(ok, ackQueue, 15)) {
                int consumed = ctx.consumeCount(false, 10, 1500); // QoS=10：broker 最多在途 10 条
                int left = ctx.ready();
                return "投 15 条 QoS=10 消费到 " + consumed + " 条后停投, ready 剩 " + left
                        + " (预期 consumed=10 left=5；确认一条即可续投)";
            }
        });

        System.out.println("\n==== 全部场景执行完毕 ====");
        // amqp 消费线程池非 daemon 会挂住 JVM（曾因占用产物 jar 导致后续构建报 Device or resource busy）
        System.exit(0);
    }

    /**
     * Ack 场景上下文：自建/自清测试队列 cr.ui.q.ack（用完即删，不留服务端残留），
     * 提供 ready / unacked 深度读数（经管理 API）与镜像面板的消费入口。
     */
    private static final class Ctx implements AutoCloseable {
        final Connection c;
        Channel ch;
        final String queue;
        final int baseline;
        final String host;
        final String vhost;
        final String user;
        final String pass;

        private Ctx(Connection c, Channel ch, String queue, int baseline,
                    String host, String vhost, String user, String pass) {
            this.c = c;
            this.ch = ch;
            this.queue = queue;
            this.baseline = baseline;
            this.host = host;
            this.vhost = vhost;
            this.user = user;
            this.pass = pass;
        }

        static Ctx open(RabbitConnection conn, String queue, int publishCount) throws Exception {
            Connection c = RabbitClient.factory(conn).newConnection();
            Channel ch = c.createChannel();
            ch.queueDeclare(queue, true, false, false, null);
            ch.queuePurge(queue);
            for (int i = 0; i < publishCount; i++) {
                ch.basicPublish("", queue, null, ("ack-check-" + i).getBytes("UTF-8"));
            }
            Thread.sleep(300);
            return new Ctx(c, ch, queue, publishCount, conn.host, conn.vhost, conn.username, conn.password);
        }

        /** 消费一条并返回 delivery tag（QoS=1 保证在途仅一条，镜像面板 Prefetch 语义）。 */
        long consumeOne(boolean autoAck, int prefetch) throws Exception {
            if (prefetch > 0) {
                ch.basicQos(prefetch);
            }
            final long[] got = {-1};
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            String tag = ch.basicConsume(queue, autoAck, "harness-ack-" + System.currentTimeMillis(),
                    false, false, null, new com.rabbitmq.client.DefaultConsumer(ch) {
                        @Override
                        public void handleDelivery(String t, com.rabbitmq.client.Envelope e,
                                                   com.rabbitmq.client.AMQP.BasicProperties p, byte[] b) {
                            got[0] = e.getDeliveryTag();
                            latch.countDown();
                        }
                    });
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
            ch.basicCancel(tag);
            Thread.sleep(200);
            return got[0];
        }

        /** 再收一条并返回其是否被标记 redeliver（用于验证 requeue 语义）。 */
        boolean consumeOneRedelivered(boolean autoAck) throws Exception {
            final boolean[] redelivered = {false};
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            String tag = ch.basicConsume(queue, autoAck, "harness-redel-" + System.currentTimeMillis(),
                    false, false, null, new com.rabbitmq.client.DefaultConsumer(ch) {
                        @Override
                        public void handleDelivery(String t, com.rabbitmq.client.Envelope e,
                                                   com.rabbitmq.client.AMQP.BasicProperties p, byte[] b) {
                            redelivered[0] = e.isRedeliver();
                            latch.countDown();
                        }
                    });
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
            ch.basicCancel(tag);
            Thread.sleep(200);
            return redelivered[0];
        }

        /** 自动确认消费 count 条，返回是否收齐。 */
        boolean consumeAll(boolean autoAck, int count) throws Exception {
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(count);
            String tag = ch.basicConsume(queue, autoAck, "harness-all-" + System.currentTimeMillis(),
                    false, false, null, new com.rabbitmq.client.DefaultConsumer(ch) {
                        @Override
                        public void handleDelivery(String t, com.rabbitmq.client.Envelope e,
                                                   com.rabbitmq.client.AMQP.BasicProperties p, byte[] b) {
                            latch.countDown();
                        }
                    });
            boolean got = latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
            ch.basicCancel(tag);
            Thread.sleep(200);
            return got;
        }

        /** 手动确认模式下限定在途上限消费，返回限定时间内实收条数（用于验证 prefetch 停投）。 */
        int consumeCount(boolean autoAck, int prefetch, long waitMs) throws Exception {
            ch.basicQos(prefetch);
            final java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
            String tag = ch.basicConsume(queue, autoAck, "harness-cnt-" + System.currentTimeMillis(),
                    false, false, null, new com.rabbitmq.client.DefaultConsumer(ch) {
                        @Override
                        public void handleDelivery(String t, com.rabbitmq.client.Envelope e,
                                                   com.rabbitmq.client.AMQP.BasicProperties p, byte[] b) {
                            n.incrementAndGet();
                        }
                    });
            Thread.sleep(waitMs);
            ch.basicCancel(tag);
            Thread.sleep(200);
            return n.get();
        }

        /** 关掉当前通道并另开一条：未确认消息由 broker 全部重投，用于确证 ack 是否生效。 */
        void cycleChannel() throws Exception {
            ch.close();
            ch = c.createChannel();
            Thread.sleep(400);
        }

        /** 即时队列深度：passive 声明取值（管理 API 有统计发布间隔，新建队列会缺字段）。 */
        int ready() {
            try {
                Channel probe = c.createChannel();
                int n = probe.queueDeclarePassive(queue).getMessageCount();
                probe.close();
                return n;
            } catch (Exception e) {
                System.out.println("[depth-warn] passive 取值失败: " + e);
                return -1;
            }
        }

        @Override
        public void close() throws Exception {
            try {
                ch.queueDelete(queue); // 自清，不留残留
            } catch (Exception ignored) {
            }
            try {
                ch.close();
            } catch (Exception ignored) {
            }
            c.close(1000);
        }
    }

    /** 镜像 ProducerPanel 的 AUTO 档：不声明交换机，直接 mandatory 发布 + ReturnListener + 800ms 等待。 */
    private static String publishAndCheckReturn(RabbitConnection conn, String exchange, String routingKey,
                                                java.util.Map<String, Object> headers, String body) throws Exception {
        Connection c = RabbitClient.factory(conn).newConnection();
        try {
            Channel ch = c.createChannel();
            java.util.List<com.rabbitmq.client.Return> returned = new java.util.concurrent.CopyOnWriteArrayList<>();
            ch.addReturnListener(returned::add);
            com.rabbitmq.client.AMQP.BasicProperties.Builder b = new com.rabbitmq.client.AMQP.BasicProperties.Builder().deliveryMode(2);
            if (headers != null && !headers.isEmpty()) {
                b.headers(headers);
            }
            ch.basicPublish(exchange, routingKey, true, b.build(), body.getBytes("UTF-8"));
            Thread.sleep(800);
            ch.close();
            if (returned.isEmpty()) {
                return "OK: 已路由到至少一个队列(无退回)";
            }
            com.rabbitmq.client.Return r = returned.get(0);
            return "退回: replyCode=" + r.getReplyCode() + " replyText=" + r.getReplyText() + " (消息未进任何队列)";
        } finally {
            c.close(1000);
        }
    }

    private static void scenario(String name, Task task) {
        System.out.println("\n########## " + name + " ##########");
        try {
            System.out.println("[结果] " + task.run());
        } catch (Throwable t) {
            System.out.println("[原始] " + t.getClass().getName() + " | getMessage()=" + t.getMessage());
            if (t.getCause() != null && t.getCause() != t) {
                System.out.println("[根因] " + t.getCause().getClass().getName() + " | getMessage()=" + t.getCause().getMessage());
            }
            System.out.println("[翻译] --- UI 将显示 ---");
            System.out.println(AmqpErrors.describe(t));
        }
    }

    interface Task {
        String run() throws Exception;
    }
}
