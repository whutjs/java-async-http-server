
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * 
 * Redis工具类,用于获取RedisPool,使用的是Jedis连接。注意AREDIS性能可能比JEDIS好
 * 参考官网说明如下：
 * You shouldn't use the same instance from different threads because you'll have strange errors.
 * And sometimes creating lots of Jedis instances is not good enough because it means lots of sockets and connections,
 * which leads to strange errors as well. A single Jedis instance is not threadsafe!
 * To avoid these problems, you should use JedisPool, which is a threadsafe pool of network connections.
 * This way you can overcome those strange errors and achieve great performance.
 * To use it, init a pool:
 * JedisPool pool = new JedisPool(new JedisPoolConfig(), "localhost");
 * You can store the pool somewhere statically, it is thread-safe.
 * JedisPoolConfig includes a number of helpful Redis-specific connection pooling defaults.
 * For example, Jedis with JedisPoolConfig will close a connection after 300 seconds if it has not been returned.
 *
 * @author lee
 *         2015-1-29
 */
public class JedisUtil {


    /**
     * 私有构造器.
     */
    private JedisUtil() {
    }

//    private static Map<String, JedisPool> maps = new HashMap<String, JedisPool>();

    private static JedisPool pool;
//    public static 
    /**
     * 获取连接池.
     *
     * @return 连接池实例
     */

    /**
     * 类级的内部类，也就是静态的成员式内部类，该内部类的实例与外部类的实例
     * 没有绑定关系，而且只有被调用到时才会装载，从而实现了延迟加载。
     */
    private static class RedisUtilHolder {
        /**
         * 静态初始化器，由JVM来保证线程安全
         */
        private static JedisUtil instance = new JedisUtil();
    }

    /**
     * 当getInstance方法第一次被调用的时候，它第一次读取
     * RedisUtilHolder.instance，导致RedisUtilHolder类得到初始化；而这个类在装载并被初始化的时候，会初始化它的静
     * 态域，从而创建RedisUtil的实例，由于是静态的域，因此只会在虚拟机装载类的时候初始化一次，并由虚拟机来保证它的线程安全性。
     * 这个模式的优势在于，getInstance方法并没有被同步，并且只是执行一个域的访问，因此延迟初始化并没有增加任何访问成本。
     */
    public static JedisUtil getInstance() {
        return RedisUtilHolder.instance;
    }
    
    private static String host;
    private static int port;
//    private static Jedis jedis;
    public static void initJedis(final String h, final int p) {
          host = h;
          port = p;
          JedisPoolConfig config = new JedisPoolConfig();
          config.setMaxTotal(320);
          config.setMaxIdle(300);
          pool = new JedisPool(config, host, port);
//      jedis = new Jedis(host, port);
    }
    /**
     * 获取Redis实例.
     *
     * @return Redis工具类实例
     */
    public Jedis getJedis() {
        Jedis jedis = null;
        jedis = pool.getResource();
        return jedis;
    }
//
    /**
     * 释放redis实例到连接池.
     *
     * @param jedis redis实例
     */
    public void closeJedis(Jedis jedis) {
        if (jedis != null) {
            pool.returnResource(jedis);
        }
    }
}