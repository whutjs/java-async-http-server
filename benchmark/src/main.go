// Go hello-world implementation for eleme/hackathon.

package main

import (
	"database/sql"
	_ "github.com/go-sql-driver/mysql"
	"github.com/garyburd/redigo/redis"
	"fmt"
	"net/http"
	"os"
	"log"
)

// 与redis有关的key
const (
	// 用Redis来存一个数字用来给三台服务器分工，返回1示服务器1，2表示服务器2.
    SERVER_IDX_KEY = "server_idx"	
    // =1服务器说明已经读写完数据	
    SERVER_COMPLETE_KEY = "server_complete"
    // 在Redis里用两个Hash存用户的name,id,psw，key=user_name_id, field=username, value=id
    USER_NAME_ID_KEY = "user_name_id"
    // field=username, value=psw
    USER_NAME_PSW_KEY = "user_name_psw"
    // 用set保存用户所拥有的购物车的id，key=user_cart_key+uid,用户每增加一个
    // 购物车就incr1，购物车cartId=(userid*100)+(user_cart_key_value)
    USER_CART_KEY = "user_cart_key"

	// 用Redis的hash维护cart和food关系。key:cart_info+cart_id value:(food_id):(cnt)	 
	CART_INFO_KEY = "cart_info";
	// 用hash存放所有已下单的用户的orderId(=userid) : cart_id
	GLOBAL_ORDER_KEY = "global_order_key";
)

var APP_HOST string
var APP_PORT string
var DB_HOST string
var DB_PORT string
var DB_NAME string
var DB_USER string
var DB_PASS string
var REDIS_HOST string
var REDIS_PORT string
var RedisClient  *redis.Pool


func getFoods(w http.ResponseWriter, r *http.Request) { 
    w.Write([]byte(r.URL.Path))
} 

func createCarts(w http.ResponseWriter, r *http.Request) { 
    w.Write([]byte("hello world!"))
} 

func login(w http.ResponseWriter, r *http.Request) { 
    w.Write([]byte("hello world!"))
} 

func initDb(){
	dbAddr := fmt.Sprintf("%s:%s@tcp(%s:%s)/%s", DB_USER, DB_PASS, DB_HOST, DB_PORT,DB_NAME)
	db, err := sql.Open("mysql", dbAddr)	
	 if err != nil {
        panic(err.Error())  // Just for example purpose. You should use proper error handling instead of panic
    }
    defer db.Close()
    // Prepare statement for inserting data
    rows, err := db.Query("select * from food")
	if err != nil {
		log.Println(err)
	}
 
	defer rows.Close()
	var id int
	var price int
	var stock int
	for rows.Next() {
		err := rows.Scan(&id, &stock, &price)
		if err != nil {
			log.Fatal(err)
		}
		log.Println(id, price, stock)
	}
	redisAddr := fmt.Sprintf("%s:%s", REDIS_HOST, REDIS_PORT)
	RedisClient = &redis.Pool{		
		MaxIdle:     1,
		MaxActive:   8,		
		Dial: func() (redis.Conn, error) {
			c, err := redis.Dial("tcp", redisAddr)
			if err != nil {
				return nil, err
			}			
			return c, nil
		},
	}

	rc := RedisClient.Get()
	defer rc.Close()
	n, err := rc.Do("SADD", "test", "hello")
	if err != nil {
		log.Println(err)
	}
	log.Println(n)
}
func main() {
	APP_HOST = os.Getenv("APP_HOST")
	APP_PORT = os.Getenv("APP_PORT")
	DB_HOST = os.Getenv("DB_HOST")
	DB_PORT = os.Getenv("DB_PORT")
	DB_NAME = os.Getenv("DB_NAME")
	DB_USER = os.Getenv("DB_USER")
	DB_PASS = os.Getenv("DB_PASS")
	REDIS_HOST = os.Getenv("REDIS_HOST")
	REDIS_PORT = os.Getenv("REDIS_PORT")
	initDb()

	if APP_HOST == "" {
		APP_HOST = "localhost"
	}
	if APP_PORT == "" {
		APP_PORT = "8080"
	}
	addr := fmt.Sprintf("%s:%s", APP_HOST, APP_PORT)
	
	http.HandleFunc("/login", login)
	http.HandleFunc("/carts", createCarts)
	http.HandleFunc("/foods", getFoods)	


	http.ListenAndServe(addr, nil)
}
