import java.io.IOException;



public class Run {
	public static void main(String[] args) throws IOException, InterruptedException {
		
		Server server = new Server(args[0], Integer.parseInt(args[1]), args[2], Integer.parseInt(args[3]), args[4], 
				args[5], args[6], args[7], Integer.parseInt(args[8]));
		server.listen();
		
	}
}
