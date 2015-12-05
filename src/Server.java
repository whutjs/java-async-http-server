import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Queue;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

public final class Server {
	
	/********* JSON KEY				***********/
	private static final String OK_200 = "200 OK";
	private static final String NOT_FOUND_404 = "404 Not Found";
	private static final String BAD_REQUEST_400 = "400 Bad Request";
	private static final String FORBIDDEN_403 = "403 Forbidden";
	private static final String UNAUTHORIZED_401 = "401 Unauthorized";
	
	private static final String ORDER_OUT_OF_LIMIT = "ORDER_OUT_OF_LIMIT";
	private static  String ORDER_OUT_OF_LIMIT_CN;
	
	private static final String EMPTY_REQUEST = "EMPTY_REQUEST";
	private static String EMPTY_REUQEST_CN ;
	
	private static final String MALFORMED_JSON = "MALFORMED_JSON";
	private static  String MALFORMED_JSON_CN;
	
	private static final String INVALID_ACCESS_TOKEN = "INVALID_ACCESS_TOKEN";
	private static  String INVALID_ACCESS_TOKEN_CN;
	
	private static final String USER_AUTH_FAIL = "USER_AUTH_FAIL";
	private static String USER_AUTH_FAIL_CN;
	
	private static final String CART_NOT_FOUND = "CART_NOT_FOUND";
	private static String CART_NOT_FOUND_CN;
	
	private static final String NOT_AUTHORIZED_TO_ACCESS_CART = "NOT_AUTHORIZED_TO_ACCESS_CART";
	private static  String NOT_AUTHORIZED_TO_ACCESS_CART_CN;
	
	private static final String FOOD_OUT_OF_LIMIT = "FOOD_OUT_OF_LIMIT";
	private static  String FOOD_OUT_OF_LIMIT_CN;
	
	private static final String FOOD_NOT_FOUND = "FOOD_NOT_FOUND";
	private static String FOOD_NOT_FOUND_CN;
	
	private static final String FOOD_OUT_OF_STOCK = "FOOD_OUT_OF_STOCK";
	private static String FOOD_OUT_OF_STOCK_CN;
	
	private static final String USER_ID_JSONKEY = "user_id";
	private static final String USER_NAME_JSONKEY = "username";
	private static final String ACCESS_TOKEN_JSONKEY = "access_token";
	
	/*****************************************/
	 private static String APP_HOST;
		private static int APP_PORT;
	private static String DB_HOST;
	private static int DB_PORT;
	private static String DB_NAME;
	private static String DB_USER;
	private static String DB_PASS;
	private static String REDIS_HOST;
	private static int REDIS_PORT;
	
	private final static String ACCESS_TOKEN_REGEX = "Access-Token:\\s(\\w+)";
	private final static Pattern tokenPattern = Pattern.compile(ACCESS_TOKEN_REGEX);
	
	private final static String CARTSID_REGEX = "/carts/(-?\\w+)(\\?)?";
	private final static Pattern cartidPattern = Pattern.compile(CARTSID_REGEX);
	
	private long serverIdx = 0;
	
	
	private final int THREADS_NUM = 300;
	private static Queue<ByteBuffer> bufferQueue = new ConcurrentLinkedQueue<ByteBuffer>();
	private static Queue<ByteBuffer> smallWriteBufferQueue = new ConcurrentLinkedQueue<ByteBuffer>();
	private static Queue<ByteBuffer> bigWriteBufferQueue = new ConcurrentLinkedQueue<ByteBuffer>();
	
	private static String getFoodsResponse = "";
	public Server(final String host, int port, final String dbHost,
			final int dbPort, final String dbName, final String dbUser, final String dbPass,
			final String redistHost, final int redistPort) {
		APP_HOST = host;
		APP_PORT = port;
		DB_HOST = dbHost;
		DB_PORT = dbPort;
		DB_NAME = dbName;
		DB_USER = dbUser;
		DB_PASS = dbPass;
		REDIS_HOST = redistHost;
		REDIS_PORT = redistPort;

		EMPTY_REUQEST_CN = "请求体为空";
		ORDER_OUT_OF_LIMIT_CN = "每个用户只能下一单";
		FOOD_OUT_OF_STOCK_CN = "食物库存不足";
		FOOD_OUT_OF_LIMIT_CN = "篮子中食物数量超过了三个";
		FOOD_NOT_FOUND_CN = "食物不存在";
		NOT_AUTHORIZED_TO_ACCESS_CART_CN = "无权限访问指定的篮子";
		CART_NOT_FOUND_CN =  "篮子不存在";
		USER_AUTH_FAIL_CN = "用户名或密码错误";
		MALFORMED_JSON_CN = "格式错误";
		INVALID_ACCESS_TOKEN_CN = "无效的令牌";
	}
		
	public void invokeFunc10k() {
		for(int i = 0; i < 10010; i++) {
			checkTokenValid(null);
			handleAddCart(null, null);
			handleFoods();
			handleLogin(null, null, null);
			handleOrders(null, null, null, null);
			handlePatch(null, null, null, null);
			DBUtil.updateStockInRedis(null, null);
		}
	}
	
	public void listen() throws IOException, InterruptedException {
		String url = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
		DBUtil.initConnection(url, DB_USER, DB_PASS);	
		JedisUtil.initJedis(REDIS_HOST, REDIS_PORT);
		initData();
		ExecutorService executor = Executors.newFixedThreadPool(THREADS_NUM); 
		AsynchronousChannelGroup asyncChannelGroup = AsynchronousChannelGroup.withThreadPool(executor); 
		AsynchronousServerSocketChannel listener = 
				AsynchronousServerSocketChannel.open(asyncChannelGroup).bind(new InetSocketAddress(APP_PORT));
		listener.accept(listener, new AioAcceptHandler());  
		asyncChannelGroup.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
	}
	
	public void initData() {
		invokeFunc10k();
		// 初始化400个directByteBuffer
		for(int i = 0; i < THREADS_NUM + 100; i++) {
			bufferQueue.offer(ByteBuffer.allocateDirect(350));
			smallWriteBufferQueue.offer(ByteBuffer.allocateDirect(4000));
		}
		// 只要20个bigWriteBuffer(>4000)
		for(int i = 0; i < 20; i++) {
			bigWriteBufferQueue.offer(ByteBuffer.allocateDirect(28000));
		}
		String url = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
		DBUtil.initConnection(url, DB_USER, DB_PASS);	
		JedisUtil.initJedis(REDIS_HOST, REDIS_PORT);
		Jedis jedis = JedisUtil.getInstance().getJedis();
		serverIdx = jedis.incr(DBUtil.SERVER_IDX_KEY);
		System.out.println("serverIdx="+serverIdx);
		if(serverIdx == 1) {
			// 由于只有一条线程访问connection，所以不用池
			Connection conn = DBUtil.getConnection();
			DBUtil.initMyFoodsDataPart(jedis, conn);
			DBUtil.initMyUsersDataPart(jedis, conn);
			jedis.set(DBUtil.SERVER_COMPLETE_KEY, "1");
			if(conn != null) {
				try {
					conn.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}else{
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			boolean exits = jedis.exists(DBUtil.SERVER_COMPLETE_KEY);
			System.out.println("SERVER_COMPLETE_KEY:" + exits);
			while(!exits) {			
				 exits = jedis.exists(DBUtil.SERVER_COMPLETE_KEY);
			}
			DBUtil.initLocalDataFromRedis(jedis);
		}
		JedisUtil.getInstance().closeJedis(jedis);
		
		getFoodsResponse = Util.createResponse(OK_200, 
				DBUtil.allFOODS_JSON);
	}
	
	private final class AioAcceptHandler implements CompletionHandler<AsynchronousSocketChannel, AsynchronousServerSocketChannel> {

		@Override
		public void completed(AsynchronousSocketChannel socket, AsynchronousServerSocketChannel attachment) {
			 attachment.accept(attachment, this); 
			 ByteBuffer clientBuffer = bufferQueue.poll();
			 while(clientBuffer == null) {
				 try {
					Thread.sleep(2);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				clientBuffer = bufferQueue.poll();
			 }
		     socket.read(clientBuffer, clientBuffer, new AioReadHandler(socket)); 
		}

		@Override
		public void failed(Throwable exc, AsynchronousServerSocketChannel attachment) {
			
		}
	}
	
	private final class AioReadHandler implements CompletionHandler<Integer, ByteBuffer> { 
	    private AsynchronousSocketChannel socket; 
	 
	    public AioReadHandler(AsynchronousSocketChannel socket) { 
	        this.socket = socket; 
	    } 
	    
	    @Override
	    public void completed(Integer i, ByteBuffer buf) { 
	        if (i > 0) { 
	            buf.flip(); 
	            String request = "";
	            byte[] bufBytes = new byte[i];
	            buf.get(bufBytes);
	            request = new String(bufBytes, 0, bufBytes.length).trim();
	            Jedis jedis = JedisUtil.getInstance().getJedis();
	            StringTokenizer strTokenizer = new StringTokenizer(request, "\r\n");
	            String firstLine = strTokenizer.nextToken();         
				String response = "";
				strTokenizer = new StringTokenizer(firstLine, " ");
				String method = strTokenizer.nextToken(), uri = strTokenizer.nextToken();
				if(uri.charAt(1) == 'l') {
					// there is no GET in login
					 response = handleLogin(method, request, jedis);
				}else{
					boolean hasToken = false;
					String token = "";
					if(uri.contains("?access")) {
						strTokenizer = new StringTokenizer(uri, "=");
						strTokenizer.nextToken();
						token = strTokenizer.nextToken();
						hasToken = true;
					}else{
						Matcher mc = tokenPattern.matcher(request);
						if(mc.find()) {
							token = mc.group(1);
							hasToken = true;
						}
					}
					boolean tokenValid = false;
					if(hasToken) {
						tokenValid = checkTokenValid(token);
					}
					if(tokenValid){
						// validate the token
						// token OK
						if(uri.charAt(1) == 'f') {
							response = handleFoods();
						}else if(method.charAt(1) == 'A'){
							// PATCH /carts/e0c68eb96bd8495dbb8fcd8e86fc48a3?access_token=xxx
							response = handlePatch(uri, request, token, jedis);
						}
						else if(uri.charAt(1) == 'c') {
							response = handleAddCart(token, jedis);
							
						}else if(uri.charAt(1) == 'a') {
							response = handleAdmin(token, jedis);
							
						}else if(uri.charAt(1) == 'o') {
							// 1,POST /orders?access_token=xxx 下单
							// 2，GET /orders?access_token=xxx  查询订单
							response = handleOrders(method, request, token, jedis);
						}
					}else{
							// 401 Unauthorized
							JsonObject resJson = new JsonObject();
							resJson.add("code", INVALID_ACCESS_TOKEN);
							resJson.add("message", INVALID_ACCESS_TOKEN_CN);
							response = Util.createResponse(UNAUTHORIZED_401, 
									resJson.toString());
					}
				}
	            JedisUtil.getInstance().closeJedis(jedis);
	            byte[] responseBytes = null;
				try {
					responseBytes = response.getBytes("utf-8");
				} catch (UnsupportedEncodingException e1) {
					e1.printStackTrace();
				}
	            ByteBuffer buffer = null;
	            if(responseBytes.length < 4000) {
	            	buffer = smallWriteBufferQueue.poll();
	            	while(buffer == null) {
		            	try {
							Thread.sleep(2);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
		            	smallWriteBufferQueue.poll();
		            }
	            }else{
	            	buffer = bigWriteBufferQueue.poll();
	            	while(buffer == null) {
		            	try {
							Thread.sleep(2);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
		            	bigWriteBufferQueue.poll();
		            }
	            }
	            
				buffer.put(responseBytes);
	    	    buffer.position(0);
	    	    buffer.limit(responseBytes.length);
	    	    try {
	    	          socket.setOption(StandardSocketOptions.SO_SNDBUF, buffer.limit());
	    	    } catch (IOException e) {
	    	          e.printStackTrace();
	    	    }
	    	    SocketWriteHandler handler = new SocketWriteHandler(socket, buffer);
	    	    socket.write(buffer, null, handler);
	    	    
	    	    buf.clear();
	    	    bufferQueue.offer(buf);
	        } else if (i == -1) { 
	            try { 
	                System.out.println("offline:" + socket.getRemoteAddress().toString()); 
	                if(socket != null) {
	                	socket.close();
	                }
	            } catch (IOException e) { 
	                e.printStackTrace(); 
	            } 
	            buf.clear();
	    	    bufferQueue.offer(buf);
	        } 
	    } 
	    @Override
	    public void failed(Throwable exc, ByteBuffer buf) { 
	        System.out.println(exc); 
	    }

	}
	
	private final class SocketWriteHandler implements CompletionHandler<Integer, Void> {

	    private AsynchronousSocketChannel socketChannel;
	    private ByteBuffer buffer;

	    public SocketWriteHandler(AsynchronousSocketChannel socketChannel, ByteBuffer buffer) {
	        this.socketChannel = socketChannel;
	        this.buffer = buffer;
	    }

	    @Override
	    public void completed(Integer result, Void attachment) {
	        if(buffer.hasRemaining()) {
	        	System.out.println("buffer.hasRemaining()");
	            socketChannel.write(buffer, null, this);
	        } else {
	            try {
	                socketChannel.close();
	            } catch (IOException e) {
	                e.printStackTrace();
	            }
	            buffer.clear();
	            if(result < 4000) {
	            	smallWriteBufferQueue.offer(buffer);
	            }else{
	            	bigWriteBufferQueue.offer(buffer);
	            }
	        }
	    }

	    @Override
	    public void failed(Throwable exc, Void attachment) {
	    	System.out.println("SocketWriteHandler, failed");
	        try {
	            socketChannel.close();
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
	    }
	}
	/**
	 * 
	 * @param cart_id
	 * @param token
	 * @return 404:Not Found   401:Unauthorized 0:Success
	 */
	private int validCardId(final String cart_id, final String token, final Jedis jedis) {
		if(cart_id == null || cart_id.isEmpty()) {
			return 404;
		}
		int resultCode = 404;
		int cartId = 0;
		try{
			cartId = Integer.parseInt(cart_id);
		}catch(NumberFormatException ex) {
			return resultCode;
		}
		cartId /= 100;
		if(cartId >= DBUtil.minUID && cartId <= DBUtil.maxUID) {
			int tokenVal = Integer.parseInt(token) >> 2;			
			if(tokenVal == cartId) {
				resultCode = 0;
			}else{
				resultCode = 401;
			}
		}else{
			resultCode = 404;
		}

		return resultCode;
	}
	
	// 在本地缓存和Redis里面检查是否缓存有foodId
	private boolean validateFoodId(final String foodId, final Jedis jedis) {
		if(foodId == null || foodId.isEmpty()) {
			return false;
		}
		boolean flag = DBUtil.foodCache.containsKey(Integer.parseInt(foodId));
		return flag;
	}
	// patch foods to cart
	private String handlePatch(final String uri, final String body, final String token, final Jedis jedis) {
		if(uri == null || uri.isEmpty() || jedis == null) {
			return null;
		}
		String response = "";
		// extract cart_id
		Matcher m = cartidPattern.matcher(uri);
		m.find();
		String cart_id = m.group(1);
		Response<String> foodCountResponse;
		int code = validCardId(cart_id, token, jedis);
		if(code == 0) {
			if(DBUtil.orderIdCache.contains(token)) {
				// 已经下过单
				response = Util.createResponse("204 No content", 
						null);
				return response;
			}else{
				Pipeline pipe = jedis.pipelined();
				Response<Boolean> exists = pipe.hexists(DBUtil.GLOBAL_ORDER_KEY, token);
				foodCountResponse = pipe.get(DBUtil.CART_FOOD_COUNT_KEY + cart_id);
				pipe.sync();
				if(exists.get()) {
					// 已经下过单
					response = Util.createResponse("204 No content", 
							null);
					return response;
				}
			}
			// extract foodid and foodcnt
			String jsonData = Util.extractPostData(body);
			JsonObject jsonObj = JsonObject.readFrom(jsonData);
			String foodid = String.valueOf(jsonObj.get("food_id").asInt());
			int cnt = jsonObj.get("count").asInt();
			// check whether foodid exist
			if(!validateFoodId(foodid, jedis)) {
				JsonObject resJson = new JsonObject();
				resJson.add("code", FOOD_NOT_FOUND);
				resJson.add("message", FOOD_NOT_FOUND_CN);
				response = Util.createResponse(NOT_FOUND_404, 
						resJson.toString());
				return response;
			}
			// 检查篮子中食物数量
//			Map<String, String> cartInfo = jedis.hgetAll(DBUtil.CART_INFO_KEY + cart_id);
			String foodCount = foodCountResponse.get();
			int cartFoodCount = 0;
			if(foodCount != null && !foodCount.isEmpty()) {
				cartFoodCount = Integer.parseInt(foodCount);
			}
			
			if(cartFoodCount + cnt <= 3) {
				// add food to cart
				Pipeline pipe = jedis.pipelined();
				pipe.hincrBy(DBUtil.CART_INFO_KEY + cart_id, foodid, cnt);
				pipe.incrBy(DBUtil.CART_FOOD_COUNT_KEY + cart_id, cnt);
				pipe.sync();				
				response = Util.createResponse("204 No content", 
						null);
			}else{
				JsonObject resJson = new JsonObject();
				resJson.add("code", FOOD_OUT_OF_LIMIT);
				resJson.add("message", FOOD_OUT_OF_LIMIT_CN);
				response = Util.createResponse(FORBIDDEN_403 ,
						resJson.toString());
			}
		}else if(code == 401) {
			JsonObject resJson = new JsonObject();
			resJson.add("code", NOT_AUTHORIZED_TO_ACCESS_CART);
			resJson.add("message", NOT_AUTHORIZED_TO_ACCESS_CART_CN);
			response = Util.createResponse(UNAUTHORIZED_401, 
					resJson.toString());
		}else if(code == 404) {
			// cart_id not exist
			JsonObject resJson = new JsonObject();
			resJson.add("code", CART_NOT_FOUND);
			resJson.add("message", CART_NOT_FOUND_CN);
			response = Util.createResponse(NOT_FOUND_404, 
					resJson.toString());
		}
		return response;
	}
	private boolean checkTokenValid(final String token) {
		if(token == null || token.isEmpty()) {
			return false;
		}
		int id = Integer.parseInt(token)  >> 2;
		if(DBUtil.userIdCache.contains(String.valueOf(id))) {
			return true;
		}
		return false;
	}
	
	private String handleOrders(final String method, 
			final String body, final String token, final Jedis jedis) {
		if(jedis == null) {
			return null;
		}
		// 1,POST /orders?access_token=xxx 下单
		// 2，GET /orders?access_token=xxx  查询订单
		String response  = "";
		if(method.charAt(0) == 'P') {
			// extract cart_id
			String jsonData = Util.extractPostData(body);
			JsonObject jsonObj = JsonObject.readFrom(jsonData);
			String cart_id = jsonObj.get("cart_id").asString();
			int cartIdCode = validCardId(cart_id, token, jedis);
			if(cartIdCode != 0) {
				JsonObject resJson = new JsonObject();
				resJson.add("code", "NOT_AUTHORIZED_TO_ACCESS_CART");
				resJson.add("message", "无权限访问指定的篮子");
				response = Util.createResponse(UNAUTHORIZED_401, 
						resJson.toString());
				return response;
			}
			if(DBUtil.orderIdCache.contains(token)) {
				// 已经下过单
				JsonObject resJson = new JsonObject();
				resJson.add("code", ORDER_OUT_OF_LIMIT);
				resJson.add("message", ORDER_OUT_OF_LIMIT_CN);
				response = Util.createResponse(FORBIDDEN_403, 
						resJson.toString());
			}else{
				
				// 到redis去检查
				Pipeline pipe = jedis.pipelined();
				Response<Boolean> exits = pipe.hexists(DBUtil.GLOBAL_ORDER_KEY, token);
				Response<Map<String, String>> foodsInCartResponse = pipe.hgetAll(DBUtil.CART_INFO_KEY+cart_id);
				pipe.sync();
				if(exits.get()) {
					// 已经下过单
					JsonObject resJson = new JsonObject();
					resJson.add("code", ORDER_OUT_OF_LIMIT);
					resJson.add("message", ORDER_OUT_OF_LIMIT_CN);
					response = Util.createResponse(FORBIDDEN_403, 
							resJson.toString());
				}else{
					// 检查食物库存
					Map<String, String> foodsInCart = foodsInCartResponse.get();
					// use transation
					boolean flag = DBUtil.updateStockInRedis(jedis, foodsInCart);
					if(flag == false) {
						JsonObject resJson = new JsonObject();
						resJson.add("code", FOOD_OUT_OF_STOCK);
						resJson.add("message", FOOD_OUT_OF_STOCK_CN);
						response = Util.createResponse(FORBIDDEN_403, 
								resJson.toString());
					}else{
						// 下单
						// token=user_id=order_id
						// save order to Redis
//						Pipeline pipe = jedis.pipelined();
						jedis.hset(DBUtil.GLOBAL_ORDER_KEY, token, cart_id);
//						pipe.sync();
						// save order_id(token) to local cache
						DBUtil.orderIdCache.add(token);
						JsonObject resJson = new JsonObject();
						resJson.add("id", token);
						response = Util.createResponse(OK_200, 
								resJson.toString());
					}
				}
			}
		}else if(method.charAt(0) == 'G') {
			// first get the cart_id
			boolean exits = DBUtil.orderCache.containsKey(token);
			if(!exits && !jedis.hexists(DBUtil.GLOBAL_ORDER_KEY, token)) {
				// 订单不存在
				response = Util.createResponse(OK_200, "[]");
				return response;
			}
			if(DBUtil.orderCache.containsKey(token)) {
				String cacheOrder = DBUtil.orderCache.get(token);
				response = Util.createResponse(OK_200, cacheOrder);
			}else{
				String cartId = jedis.hget(DBUtil.GLOBAL_ORDER_KEY, token);
				// then get the food info from Redis
				Map<String, String> foodsInCart = jedis.hgetAll(DBUtil.CART_INFO_KEY + cartId);
				// generate Json and save to local cache
	//			Order order = new Order();
				int total = 0;
				JsonArray items = new JsonArray();
				Map<Integer, Food> cache = DBUtil.foodCache;
				for(String food_id : foodsInCart.keySet()) {
					int food_id_int = Integer.parseInt(food_id);
					int count = Integer.parseInt(foodsInCart.get(food_id));
	//				order.items.add(new Pair(food_id_int, count));
					JsonObject itemObj = new JsonObject();
					itemObj.add("food_id", food_id_int);
					itemObj.add("count", count);
					items.add(itemObj);
					int price = 0;
					if(cache.containsKey(food_id_int)) {
						price = cache.get(food_id_int).price;
					}else{
						price = Integer.parseInt(jedis.hget(DBUtil.FOOD_ID_PRICE_KEY, food_id));
					}
					total = total + (price * count);
				}
				int uid = Integer.parseInt(token) >> 2;
				JsonObject orderJson = new JsonObject();
				orderJson.add("id", token);
				orderJson.add(USER_ID_JSONKEY, uid);
				orderJson.add("items", items);
				orderJson.add("total", total);
				JsonArray orderArray = new JsonArray();
				orderArray.add(orderJson);
				DBUtil.orderCache.put(token, orderArray.toString());
				response = Util.createResponse(OK_200, orderArray.toString());
			}
		}
		return response;
	}
	
	private String handleAdmin(final String token, final Jedis jedis) {
		String response = "";
		if(token.equals(DBUtil.rootToken) || jedis.sismember(DBUtil.ROOT_TOKEN_KEY, token)){
			// get all order_id(user_id or token) from redis
			// key->order_id(user_id,token), value->cart_id
			Map<String, String> allOrder = jedis.hgetAll(DBUtil.GLOBAL_ORDER_KEY);
			// 如果一个order_id在本地缓存有，就先取本地缓存的Order就好
			JsonArray orderArray = new JsonArray();
			for(String orderId : allOrder.keySet()) {
				String cartId = jedis.hget(DBUtil.GLOBAL_ORDER_KEY, orderId);
				// then get the food info from Redis
				Map<String, String> foodsInCart = jedis.hgetAll(DBUtil.CART_INFO_KEY + cartId);
				// generate Json and save to local cache
				JsonArray items = new JsonArray();
				Map<Integer, Food> cache = DBUtil.foodCache;
				int total = 0;
				for(String food_id : foodsInCart.keySet()) {
					int food_id_int = Integer.parseInt(food_id);
					int count = Integer.parseInt(foodsInCart.get(food_id));
					JsonObject itemObj = new JsonObject();
					itemObj.add("food_id", food_id_int);
					itemObj.add("count", count);
					items.add(itemObj);
					int price = 0;
					if(cache.containsKey(food_id_int)) {
						price = cache.get(food_id_int).price;
					}else{
						price = Integer.parseInt(jedis.hget(DBUtil.FOOD_ID_PRICE_KEY, food_id));
					}
					total = total + (price * count);
				}
				int uid = Integer.parseInt(orderId) >> 2;
				JsonObject orderJson = new JsonObject();
				orderJson.add("id", orderId);
				orderJson.add(USER_ID_JSONKEY, uid);
				orderJson.add("items", items);
				orderJson.add("total", total);
				orderArray.add(orderJson);
				
			}
			response = Util.createResponse(OK_200, orderArray.toString());
		}else{
			// not define
		}
		
		return response;
	}
	
	// add User's cart
	private String handleAddCart(final String token, final Jedis jedis) {
		if(token == null || token.isEmpty()) {
			return null;
		}
		String response = "";
		int uid = Integer.parseInt(token) >> 2;
		long idx = jedis.incr(DBUtil.USER_CART_KEY + uid);
		int cart_id = (int) (uid * 100 + idx);		
		
		JsonObject resJson = new JsonObject();
		resJson.add("cart_id", cart_id + "");
		response = Util.createResponse(OK_200, 
				resJson.toString());
		return response;
		
	}
	// method:post or get
	private String  handleLogin(final String method, final String body, final Jedis jedis) {
		if(method == null || jedis == null) {
			return null;
		}
		String response = "";
		if(method.charAt(0) == 'P') {
			boolean inRedis = false;
			boolean inLocal = false;
			String postData = Util.extractPostData(body);
			if(postData == null) {
				JsonObject resJson = new JsonObject();
				resJson.add("code", EMPTY_REQUEST);
				resJson.add("message", EMPTY_REUQEST_CN);
				response = Util.createResponse(BAD_REQUEST_400, 
						resJson.toString());
			}else{
				int strLen = postData.length();
				if(postData.charAt(0) == '{'  && postData.charAt(strLen - 1) == '}') {
					// right form
					JsonObject object = JsonObject.readFrom(postData);
					String username = object.get(USER_NAME_JSONKEY).asString();
					String password = object.get("password").asString();
					
					User user = null;
					// 首先在本地缓存检查
					inLocal = DBUtil.userCache.containsKey(username);
					if(inLocal){
						user = DBUtil.userCache.get(username);
					}else {
						JsonObject resJson = new JsonObject();
						resJson.add("code", USER_AUTH_FAIL);
						resJson.add("message", USER_AUTH_FAIL_CN);
						response = Util.createResponse(FORBIDDEN_403, 
								resJson.toString());
						return response;
			
					}
					if((inLocal || inRedis) && user == null) {
						if(inLocal){
							System.out.println(username + " inLocal but user==null");
						}
						if(inRedis){
							System.out.println(username + " inRedis but user==null");
						}
					}
					if(user.psw.equals(password)) {
						// 
						user.accessToken = String.valueOf((user.id << 2));
						
						JsonObject resJson = new JsonObject();
						resJson.add(USER_ID_JSONKEY, user.id);
						resJson.add(USER_NAME_JSONKEY, user.name);
						resJson.add(ACCESS_TOKEN_JSONKEY, user.accessToken);
						response = Util.createResponse(OK_200, 
								resJson.toString());
						if(user.name.equals("root")) {
							DBUtil.rootToken = user.accessToken;
							jedis.sadd(DBUtil.ROOT_TOKEN_KEY, user.accessToken);
						}
						
					}else{
						JsonObject resJson = new JsonObject();
						resJson.add("code", USER_AUTH_FAIL);
						resJson.add("message", USER_AUTH_FAIL_CN);
						response = Util.createResponse(FORBIDDEN_403, 
								resJson.toString());
					}
				}else{
					JsonObject resJson = new JsonObject();
					resJson.add("code", MALFORMED_JSON);
					resJson.add("message", MALFORMED_JSON_CN);
					response = Util.createResponse(BAD_REQUEST_400, 
							resJson.toString());
				}
			}
		}
		
		return response;
	}
	
	// query the food
	private String handleFoods() {
		return getFoodsResponse;
	}
}