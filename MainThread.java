
/**
 * Write a description of class MainThread here.
 * 
 * @author (your name) 
 * @version (a version number or a date)
 */
import java.net.*;
import java.io.*;
import java.awt.*;
import javax.swing.JOptionPane;
public class MainThread extends Thread{
	private Socket sock;
	private DataOutputStream out;
	private DataInputStream in;
	public boolean running=false;
	private String password;
	private Robot robot;
	private Point prevPoint; 	//we need to keep some history. Read below!!!
	/*
	 * constructor. The password should be hashed!
	 */
	public MainThread(Socket s, String password){
		//System.out.println("mainthreadsdfsfsfsdfdf"+password);
		try{
			this.sock=s;
			this.password=password;
			out=new DataOutputStream(s.getOutputStream());
			in=new DataInputStream(s.getInputStream());

			/***************************
			 * new init for Robot with respect to the screen at point 0,0. We need this because, as in my case, if the primary screen does not contain
			 * the point 0, 0 of the pointer location we have problems.
			 ***************************/
			GraphicsDevice myGD = null;
			GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
			GraphicsDevice[] gs = ge.getScreenDevices();
			for(GraphicsDevice gd: gs) {
				GraphicsConfiguration[] gc = gd.getConfigurations();
				if(gc.length > 0) {
					Rectangle rect = gc[0].getBounds();
					if(rect.x == 0 && rect.y == 0) {
						myGD = gd;
					}
				}
			}
			if(myGD != null) {
				robot=new Robot(gs[1]);
			}
			else {
				System.out.println("Could not find 0 0 screen");
				System.exit(-1);
			}
			Runtime.getRuntime().addShutdownHook(new MouseReleaseThread());
			//System.out.println("Password: " + password);
		}
		catch(AWTException ae){
			System.err.println("Robot init failed: " + ae);
		}
		catch(Exception e){
			System.err.println("Error during MainThread init: " + e);
		}
		if(!authenticate()){
			try{
				sock.close();
				JOptionPane.showMessageDialog(null, "The password was incorrect.");
				System.exit(0);
			}
			catch(Exception e){
				e.printStackTrace();
				System.err.println(e);
				System.exit(42);
			}
		}
	}
	public boolean authenticate(){
		//System.out.println(password);
		try{
			if(in.read()!=0){
				return false;
			}
			if(password.equals(in.readUTF())){
				return true;
			}
			return false;
		}
		catch(Exception e){
			e.printStackTrace();
			System.out.println("Auth fail:" + e);
			return false;
		}
	}
	public void run(){
		running=true;
		System.out.println("MainThread running..");
		while(running&&!sock.isClosed()){
			try{
				int id=in.read();
				System.out.println(id);
				switch(id){
				case 1: //keyboard keydown
				robot.keyPress((int)in.readShort());
				break;
				case 2: //keyboard keyUp
					robot.keyRelease((int)in.readShort());
					break;
				case 3:
					robot.mousePress((int)in.readByte());
					break;
				case 4:
					robot.mouseRelease((int)in.readByte());
					break;
				case 5:
					/***************************************
					 * it has a problem that if the point goes outside the visible area the getPointerInfo throws a nullPointerException
					 * for example in my case the screen setup is:
					 * ----------------------
					 * |        |           |
					 * |        |           |
					 * ---------|           |
					 *      x   -------------
					 * 
					 * so if the pointer goes to 'x' it is not visible and null pointer exception is thrown(all other edges are checked by the X windows 
					 * system and it does not allow the mouse to go outside). I have temporarily fixed it by keep a history and setting the point to the
					 * last known position. A better fix would be check that the pointer is not out of bounds. Which can be slightly complicated if multiple
					 * screens are arranged in some weird way. I have only tested this in Linux. Windows may not allow the pointer to go there.
					 ***************************************/
					Point curPoint;
					try {
						curPoint=MouseInfo.getPointerInfo().getLocation();
					} catch(NullPointerException e) {
						System.out.println("NulpointerException at point 1");
						curPoint = prevPoint;
					}
					robot.mouseMove(curPoint.x + in.readInt(), curPoint.y + in.readInt());
					prevPoint = curPoint;
					break;
				case 6: //mouseWheel
					robot.mouseWheel(in.readByte());
					break;
				case -1:
					System.out.println("Disconnected");                   
					running=false;
					System.exit(0);
					break;
				default:
					System.err.println("unrecognized packet: " + id);
					running=false;
					break;
				}
			}
			catch(IOException ie){
				System.err.println("IOException: " + ie);
				break;
			}

		}
		try{
			sock.close();
		}
		catch(Exception e){
		}
		running=false;
	}
	private class MouseReleaseThread extends Thread{
		public void run(){
			System.out.println("releasing mouse buttons");
			robot.mouseRelease(16|8|4);
			System.out.println("released mouse buttons");
		}
	}

}
