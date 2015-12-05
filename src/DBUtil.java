import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Transaction;

public class DBUtil {
	public static int minUID = 100000000, maxUID = -1;
	// 用Redis来存一个数字用来给三台服务器分工，返回1示服务器1，2表示服务器2.
	public static final String SERVER_IDX_KEY = "server_idx";
	// idx=1服务器是否已经读写完数据
	public static final String SERVER_COMPLETE_KEY = "server_complete";
	
	// 在Redis里用两个Hash存用户表，key=user_name_id, field=username, value=id
	public static final String USER_NAME_ID_KEY = "user_name_id";
	// key=user_name_psw, field=username, value=psw
	public static final String USER_NAME_PSW_KEY = "user_name_psw";
	
	// 用set保存用户所拥有的购物车的id，key=uid
	public static final String USER_CART_KEY = "user_cart_key";
	/*
	 * 用Redis的hash维护cart和food关系。key:cart_info+cart_id value:(food_id):(cnt)
	 */
	public static final String CART_INFO_KEY = "cart_info";
	//  用来统计一个购物车里总共有多少食物了,key = cart_food_count + cart_id
	public static final String CART_FOOD_COUNT_KEY = "cart_food_count";
	
	// 用hash存放所有已下单的用户的orderId : cart_id
	public static final String GLOBAL_ORDER_KEY = "global_order_key";
	
	/* 由于这些缓存都不会修改，都只是取值和存值，所以应该不用线程同步 */
	// key=username,将用户缓存在本地
	public static Map<String, User> userCache = new HashMap<String, User>(51100);
	public static Set<String> userIdCache = new HashSet<String>(51100);
	
	// 用来存放root_token
	public static String rootToken = "";
	public static String ROOT_TOKEN_KEY = "root_token_key";
	
	public static Map<Integer, Food> foodCache = new HashMap<Integer, Food>(140);
	
	// Redis的一个Hash，用来存food_id和price（其实不用的，因为本地缓存肯定会有了）
	public static final String FOOD_ID_PRICE_KEY = "food_id_price";
	// Redis的一个hash用来保存food_id->food_stock
	public static final String FOODID_STOCK_KEY = "foodid_stock_key";
	// 因为食物顺序有要求，所以干脆一开始就将json保存到redis里
	public static final String ALLFOOD_JSON_KEY = "all_food_json_key";
	
	public static Set<String> orderIdCache = new HashSet<String>(51000);
	// 我们需要的是order的json，所以应该缓存order的json格式的数据
	public static Map<String, String> orderCache = new HashMap<String, String>(51000);
	static{
		try {
			Class.forName("com.mysql.jdbc.Driver");
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}
	private static String dbUrl, dbUser, dbPsw;
//	private static  DataSource dataSource;
//	 只用一条连接
	private static Connection conn;
	public static void initConnection(final String url, final String user, final String psw) {
		dbUrl = url;
		dbUser = user;
		dbPsw = psw;
		try {
			if(conn != null) {
				conn = DriverManager.getConnection(url, user, psw);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
	}
	
	public static Connection getConnection() {
		try {
			if(conn == null || conn.isClosed()) {
				conn = DriverManager.getConnection(dbUrl, dbUser, dbPsw);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return conn;
//		
	}
	
	public static boolean updateStockInRedis(final Jedis jedis, final Map<String, String> pairs) {
		if(jedis == null) {
			return false;
		}
		boolean flag = true;
		Pipeline pipe = jedis.pipelined();
		List<redis.clients.jedis.Response<?>> responses = new ArrayList<>(4);
		for(String foodId : pairs.keySet()) {
			long count = Long.parseLong(pairs.get(foodId));
			responses.add(pipe.hincrBy(FOODID_STOCK_KEY, foodId, -count));
		}
		pipe.sync();
		for(redis.clients.jedis.Response<?> response : responses) {
			Long remain = (Long) response.get();
			if(remain < 0){
				flag = false;
				break;
			}
		}
		return flag;
	}
	
	// {"id": 1, "price": 12, "stock": 99}
	public static String createFoodJson(int id, int stock, int price) {
		StringBuilder builder = new StringBuilder();
		builder.append("{\"id\": ").append(id+",")
		.append("\"price\": ").append(price+",")
		.append("\"stock\": ").append(stock+"}");
		return builder.toString();
	}
	
	// 直接从Redis里读数据保存在本地
	public static void initLocalDataFromRedis(final Jedis jedis) {
		Pipeline pl = jedis.pipelined();
		redis.clients.jedis.Response<Map<String,String>> foodIdPriceResponse = pl.hgetAll(FOOD_ID_PRICE_KEY);
		redis.clients.jedis.Response<Map<String,String>> foodIdStockResponse = pl.hgetAll(FOODID_STOCK_KEY);
		redis.clients.jedis.Response<Map<String,String>> nameIdResponse = pl.hgetAll(USER_NAME_ID_KEY);
		redis.clients.jedis.Response<Map<String,String>> namePswResponse = pl.hgetAll(USER_NAME_PSW_KEY);
		redis.clients.jedis.Response<String> foodsJson = pl.get(ALLFOOD_JSON_KEY);
		pl.sync();
		
		Map<String, String> stockPair = foodIdStockResponse.get();
		Map<String, String> pricePair = foodIdPriceResponse.get();
		for(String foodId : pricePair.keySet()) {
			int id = Integer.parseInt(foodId);
			int price = Integer.parseInt(pricePair.get(foodId));
			int stock = Integer.parseInt(stockPair.get(foodId));
			foodCache.put(id, new Food(id, stock, price));
		}
		allFOODS_JSON = foodsJson.get();
		
		Map<String, String> nameIdPair = nameIdResponse.get();
		Map<String, String> namePswPair = namePswResponse.get();
		for(String name : nameIdPair.keySet()) {
			User usr = new User();
			usr.name = name;
			usr.id = Integer.parseInt(nameIdPair.get(name));
			if(usr.id < minUID ) {
				minUID = usr.id;
			}else if(usr.id > maxUID) {
				maxUID = usr.id;
			}
			usr.psw = namePswPair.get(name);
			userCache.putIfAbsent(name, usr);
			userIdCache.add(nameIdPair.get(name));
		}
	}
	
	/**
	 * 如果是三台服务器的情况，那么让三台服务器分工，各取1/3食物数据
	 * @param startId (1~33)(34~66)(67~100)
	 * @param endId
	 */
	public static void initMyFoodsDataPart(final Jedis jedis, final Connection dbConn) {
//		int length = endId - startId + 1;
		Map<String, String> pair = new HashMap<String, String>(120);
		Map<String, String> stockPair = new HashMap<String, String>(120);
		// TODO MYSQL数据库查询时应该也能优化
		String sql = "select * from food";
		try {
			PreparedStatement ps = dbConn.prepareStatement(sql);
//			ps.setInt(1, startId);
//			ps.setInt(2, endId);
//			ps.setInt(3, length);
			ResultSet rs = ps.executeQuery();
			
			StringBuilder allFoodsJsonBuilder = new StringBuilder();
			allFoodsJsonBuilder.append("[");
			boolean first = true;
			while(rs.next()) {
				int id = rs.getInt(1);
				int stock = rs.getInt(2);
				int price = rs.getInt(3);			
				if(!first) {
					allFoodsJsonBuilder.append(",");
				}
				first = false;
				allFoodsJsonBuilder.append(createFoodJson(id, stock, price));
				
				foodCache.put(id, new Food(id, stock, price));
				
				String idStr = String.valueOf(id);
				String priceStr =  String.valueOf(price);
				String stockStr = String.valueOf(stock);
				pair.put(idStr, priceStr);
				stockPair.put(idStr, stockStr);
			}
			allFoodsJsonBuilder.append("]");
			allFOODS_JSON = allFoodsJsonBuilder.toString();
			
			Pipeline pl = jedis.pipelined();
			pl.hmset(FOOD_ID_PRICE_KEY, pair);
			pl.hmset(FOODID_STOCK_KEY, stockPair);
			pl.append(ALLFOOD_JSON_KEY, allFOODS_JSON);
			pl.sync();
			
			rs.close();
			ps.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	// 同理
	public static void initMyUsersDataPart(final Jedis jedis, final Connection dbConn) {
		// TODO MYSQL数据库查询时应该也能优化
		String sql = "select * from user";
		try {
			PreparedStatement ps = dbConn.prepareStatement(sql);
			ResultSet rs = ps.executeQuery();
			// use pipeline
			Pipeline pipe = jedis.pipelined();
			while(rs.next()) {
				User user = new User();
				user.id = rs.getInt(1);
				if(user.id < minUID ) {
					minUID = user.id;
				}else if(user.id > maxUID) {
					maxUID = user.id;
				}
				user.name = rs.getString(2);
				user.psw = rs.getString(3);
				userCache.putIfAbsent(user.name, user);
				userIdCache.add(String.valueOf(user.id));
				pipe.hset(USER_NAME_ID_KEY, user.name, String.valueOf(user.id));
				pipe.hset(USER_NAME_PSW_KEY, user.name, user.psw);
//				pipe.hset(DBUtil.USER_INFO_KEY + user.name, String.valueOf(user.id), user.psw);
			}
			pipe.sync();
			
			rs.close();
			ps.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public static  String allFOODS_JSON = null;
	public static final String FOOD_JSON_ID = "id";
	public static final String FOOD_JSON_STOCK = "stock";
	public static final String FOOD_JSON_PRICE = "price";
	public static String getAllFoodsJson(final Jedis jedis, int serverIdx) {
		return allFOODS_JSON;
	}
	
}