
public class Util {
	public static final String HTTP1_1 = "HTTP/1.1 ";
	public static final String CONTENT_TYPE = "Content-Type: application/json\r\n";
	public static final String CONTENT_LENGTH = "Content-Length: ";
	public static String createResponse(final String status, final String body) {
//		int length = 0;
//		if(body != null) {
//			length = body.length();
//		}
		StringBuilder build = new StringBuilder();
		build.append(HTTP1_1).append(status).append("\r\n\r\n");
//		.append(CONTENT_TYPE)
//		.append(CONTENT_LENGTH).append(length).append("\r\n\r\n");
		if(body != null) {
			build.append(body);
		}
		return build.toString();
	}
	
	public static String extractPostData(final String pose) {
		String http_data = null;
		String[] splits = pose.split("\r\n\r\n");	
		
		if(splits.length <= 1)
			return null;
		http_data = splits[1];
		if(http_data != null && http_data.length()>0){
			return http_data;
		}
		return null;	

	}


	public static String conver2UTF_8(String str) throws Throwable {        
		return new String(str.getBytes("GBK"), "UTF-8");    
	}
}
