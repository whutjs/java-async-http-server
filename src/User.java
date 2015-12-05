
public class User {
	public int id = -1;
	public String name;
	public String psw;
	public String accessToken;
	
	public User() {
		
	}
	
	public User(final int id, final String n, final String p, final String at) {
		this.id = id;
		this.name = n;
		this.psw = p;
		this.accessToken = at;
	}
}
