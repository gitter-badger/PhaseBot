package xyz.jadonfowler.phasebot;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Arrays;
import java.util.Random;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.spacehq.mc.auth.exception.AuthenticationException;
import org.spacehq.mc.protocol.MinecraftProtocol;
import org.spacehq.mc.protocol.ProtocolConstants;
import org.spacehq.mc.protocol.ProtocolMode;
import org.spacehq.mc.protocol.data.game.Chunk;
import org.spacehq.mc.protocol.data.message.Message;
import org.spacehq.mc.protocol.data.status.ServerStatusInfo;
import org.spacehq.mc.protocol.data.status.handler.ServerInfoHandler;
import org.spacehq.mc.protocol.data.status.handler.ServerPingTimeHandler;
import org.spacehq.mc.protocol.packet.ingame.client.ClientChatPacket;
import org.spacehq.mc.protocol.packet.ingame.client.player.ClientPlayerPositionRotationPacket;
import org.spacehq.mc.protocol.packet.ingame.client.player.ClientSwingArmPacket;
import org.spacehq.mc.protocol.packet.ingame.server.ServerChatPacket;
import org.spacehq.mc.protocol.packet.ingame.server.ServerJoinGamePacket;
import org.spacehq.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket;
import org.spacehq.mc.protocol.packet.ingame.server.world.ServerChunkDataPacket;
import org.spacehq.mc.protocol.packet.ingame.server.world.ServerMultiChunkDataPacket;
import org.spacehq.packetlib.Client;
import org.spacehq.packetlib.Session;
import org.spacehq.packetlib.event.session.DisconnectedEvent;
import org.spacehq.packetlib.event.session.PacketReceivedEvent;
import org.spacehq.packetlib.event.session.SessionAdapter;
import org.spacehq.packetlib.tcp.TcpSessionFactory;

import xyz.jadonfowler.phasebot.util.Vector3d;

public class PhaseBot {

	private static String USERNAME = "username";
	private static String PASSWORD = "password";
	private static String HOST = "mort.openredstone.org";
	private static int PORT = 25569;
	private static Proxy PROXY = Proxy.NO_PROXY;
	private static boolean VERIFY_USERS = true;
	public static Random random = new Random();

	static Bot bot;

	public static void main(String... args) {
		try {
			BufferedReader br = new BufferedReader(new FileReader("res/config.txt"));
			String line = br.readLine();
			while (line != null) {
				if (line.startsWith("Username: ")) { // Username:Notch
					USERNAME = line.split(": ")[1];
				} else if (line.startsWith("Password: ")) { // Password:Derp
					PASSWORD = line.split(": ")[1];
				} else if (line.startsWith("Server: ")) { // Server:minecraft.net:25565
					HOST = line.split(": ")[1];
					PORT = java.lang.Integer.parseInt(line.split(":")[2]);
				} else if (line.startsWith("Proxy")) { // Proxy:123.456.789:860
					PROXY = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(line.split(":")[1],
							java.lang.Integer.parseInt(line.split(":")[2])));
				}
				line = br.readLine();
			}
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		bot = new Bot(USERNAME, PASSWORD, HOST, PORT, PROXY);
		// status();
		login();
	}

	@SuppressWarnings("unused")
	private static void status() {
		MinecraftProtocol protocol = new MinecraftProtocol(ProtocolMode.STATUS);
		Client client = new Client(HOST, PORT, protocol, new TcpSessionFactory(PROXY));
		client.getSession().setFlag(ProtocolConstants.SERVER_INFO_HANDLER_KEY, new ServerInfoHandler() {
			@Override
			public void handle(Session session, ServerStatusInfo info) {
				System.out.println("Version: " + info.getVersionInfo().getVersionName() + ", "
						+ info.getVersionInfo().getProtocolVersion());
				System.out.println("Player Count: " + info.getPlayerInfo().getOnlinePlayers() + " / "
						+ info.getPlayerInfo().getMaxPlayers());
				System.out.println("Players: " + Arrays.toString(info.getPlayerInfo().getPlayers()));
				System.out.println("Description: " + info.getDescription().getFullText());
				System.out.println("Icon: " + info.getIcon());
			}
		});

		client.getSession().setFlag(ProtocolConstants.SERVER_PING_TIME_HANDLER_KEY, new ServerPingTimeHandler() {
			@Override
			public void handle(Session session, long pingTime) {
				System.out.println("Server ping took " + pingTime + "ms");
			}
		});

		client.getSession().connect();
		while (client.getSession().isConnected()) {
			try {
				Thread.sleep(5);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private static void login() {
		MinecraftProtocol protocol = null;
		if (VERIFY_USERS) {
			try {
				protocol = new MinecraftProtocol(USERNAME, PASSWORD, false);
				System.out.println("Successfully authenticated user.");
			} catch (AuthenticationException e) {
				e.printStackTrace();
				return;
			}
		} else {
			protocol = new MinecraftProtocol(USERNAME);
		}

		Client client = new Client(HOST, PORT, protocol, new TcpSessionFactory(PROXY));
		client.getSession().addListener(new SessionAdapter() {
			@Override
			public void packetReceived(PacketReceivedEvent event) {

				if (event.getPacket() instanceof ServerJoinGamePacket) {
					event.getSession().send(new ClientChatPacket("PhaseBot has joined the game."));
					bot.entityId = event.<ServerJoinGamePacket> getPacket().getEntityId();
				} else if (event.getPacket() instanceof ServerPlayerPositionRotationPacket) {
					bot.pos.x = event.<ServerPlayerPositionRotationPacket> getPacket().getX();
					bot.pos.y = event.<ServerPlayerPositionRotationPacket> getPacket().getY();
					bot.pos.z = event.<ServerPlayerPositionRotationPacket> getPacket().getZ();
					bot.pitch = event.<ServerPlayerPositionRotationPacket> getPacket().getPitch();
					bot.yaw = event.<ServerPlayerPositionRotationPacket> getPacket().getYaw();
					System.out.println("Err, My Position: " + bot.pos.x + "," + bot.pos.y + "," + bot.pos.z);
					event.getSession().send(
							new ClientPlayerPositionRotationPacket(false, bot.pos.x, bot.pos.y, bot.pos.z, bot.pitch,
									bot.yaw));
					// } else if (event.getPacket() instanceof
					// ServerMultiChunkDataPacket) {
					// for (Chunk c : event.<ServerChunkDataPacket>
					// getPacket().getChunks()) {
					// c.getBlocks();
					// }
				}

				else if (event.getPacket() instanceof ServerChatPacket) {

					Message message = event.<ServerChatPacket> getPacket().getMessage();
					System.out.println(message.getFullText());
					try {
						String c = message.getFullText().split(": ")[1];
						if (!c.startsWith("."))
							return;
						if (!(message.getFullText().contains("Phase") || message.getFullText().contains("Voltz")
								|| message.getFullText().contains("chibill") || message.getFullText().contains("tyler"))) {
							// event.getSession().send(new
							// ClientChatPacket("You're not my master! D:"));
							return;
						}
						if (c.startsWith(".swing")) {
							bot.swing(event.getSession());
						} else if (c.startsWith(".crouch")) {

						} else if (c.startsWith(".look")) {
							float p = Float.parseFloat(c.split(" ")[1]);
							float y = Float.parseFloat(c.split(" ")[2]);
							bot.look(event.getSession(), y, p);
						} else if (c.startsWith(".say")) {
							StringBuilder text = new StringBuilder();
							for (int i = 1; i < c.split(" ").length; i++) {
								text.append(c.split(" ")[i] + " ");
							}
							event.getSession().send(new ClientChatPacket(text.toString()));
						} else if (c.startsWith(".move ")) {
							double rx = Double.parseDouble(c.split(" ")[1]);
							double ry = Double.parseDouble(c.split(" ")[2]);
							double rz = Double.parseDouble(c.split(" ")[3]);
							System.out.println("Move: " + (bot.pos.x + rx) + " " + (bot.pos.y + ry) + " "
									+ (bot.pos.z + rz));
							move(event, rx, ry, rz);
						} else if (c.startsWith(".patrol ")) {
							double times = Double.parseDouble(c.split(" ")[1]);
							for (int i = 0; i < times; i++) {
								move(event, Math.abs(bot.positions[0].x - bot.pos.x),
										Math.abs(bot.positions[0].y - bot.pos.y),
										Math.abs(bot.positions[0].z - bot.pos.z));
								move(event, Math.abs(bot.positions[1].x - bot.pos.x),
										Math.abs(bot.positions[1].y - bot.pos.y),
										Math.abs(bot.positions[1].z - bot.pos.z));
								move(event, Math.abs(bot.positions[2].x - bot.pos.x),
										Math.abs(bot.positions[2].y - bot.pos.y),
										Math.abs(bot.positions[2].z - bot.pos.z));
								move(event, Math.abs(bot.positions[3].x - bot.pos.x),
										Math.abs(bot.positions[3].y - bot.pos.y),
										Math.abs(bot.positions[3].z - bot.pos.z));
							}
							move(event, Math.abs(bot.positions[0].x - bot.pos.x),
									Math.abs(bot.positions[0].y - bot.pos.y), Math.abs(bot.positions[0].z - bot.pos.z));
							event.getSession().send(new ClientChatPacket("I have finished patrolling!"));
						} else if (c.startsWith(".set")) {
							switch (c.split(" ")[1]) {
							case "p0":
								bot.positions[0] = bot.pos;
								event.getSession().send(
										new ClientChatPacket("p0 is set to " + (int) bot.pos.x + " " + (int) bot.pos.y
												+ " " + (int) bot.pos.z));
								break;
							case "p1":
								bot.positions[1] = bot.pos;
								event.getSession().send(
										new ClientChatPacket("p1 is set to " + (int) bot.pos.x + " " + (int) bot.pos.y
												+ " " + (int) bot.pos.z));
								break;
							case "p2":
								bot.positions[2] = bot.pos;
								event.getSession().send(
										new ClientChatPacket("p2 is set to " + (int) bot.pos.x + " " + (int) bot.pos.y
												+ " " + (int) bot.pos.z));
								break;
							case "p3":
								bot.positions[3] = bot.pos;
								event.getSession().send(
										new ClientChatPacket("p3 is set to " + (int) bot.pos.x + " " + (int) bot.pos.y
												+ " " + (int) bot.pos.z));
								break;
							}
						} else if (c.startsWith(".tp")) {
							event.getSession().send(new ClientChatPacket("/tp " + c.split(" ")[1]));
						} else if (c.startsWith(".derp")) {
							if(bot.isDerp){
								bot.isDerp = false;
							}else{
								bot.isDerp = true;
								bot.derp(event.getSession());
							}
						} else if (c.startsWith(".js")) {
							ScriptEngineManager mgr = new ScriptEngineManager();
							ScriptEngine engine = mgr.getEngineByName("JavaScript");
							StringBuilder text = new StringBuilder();
							for (int i = 1; i < c.split(" ").length; i++) {
								text.append(c.split(" ")[i] + " ");
							}
							event.getSession().send(new ClientChatPacket("js> " + engine.eval(text.toString())));
						} else if (c.startsWith(".ruby")) {
							ScriptEngineManager mgr = new ScriptEngineManager();
							ScriptEngine engine = mgr.getEngineByName("jruby");
							StringBuilder text = new StringBuilder();
							for (int i = 1; i < c.split(" ").length; i++) {
								text.append(c.split(" ")[i] + " ");
							}
							event.getSession().send(new ClientChatPacket("ruby> " + engine.eval(text.toString())));
						}
					} catch (Exception e) {
					}
				}
			}

			@Override
			public void disconnected(DisconnectedEvent event) {
				System.out.println("Disconnected: " + Message.fromString(event.getReason()).getFullText());
			}
		});

		client.getSession().connect();
	}

	public static void move(PacketReceivedEvent event, double rx, double ry, double rz) {

		double l = (bot.pos.x + rx) - bot.pos.x;
		double w = (bot.pos.z + rz) - bot.pos.z;
		double c = Math.sqrt(l * l + w * w);
		double a1 = -Math.asin(l / c) / Math.PI * 180;
		double a2 = Math.acos(w / c) / Math.PI * 180;
		if (a2 > 90)
			bot.yaw = (float) (180 - a1);
		else
			bot.yaw = (float) a1;

		// double d = Math.sqrt(Math.pow(((bot.pos.x + rx) - bot.pos.x), 2) +
		// Math.pow(((bot.pos.y + ry) - bot.pos.y), 2));
		// System.out.println(d);
		// System.out.println((bot.pos.y + ry) - bot.pos.y);
		// bot.pitch = (float) Math.asin((bot.pos.y + ry)/d);
		// System.out.println("p: " + bot.pitch + " y:" + bot.yaw);

		// bot.pitch = (float) ((ry == 0) ? 0:
		// (Math.asin(Math.sqrt(bot.pos.x*bot.pos.x+bot.pos.y+bot.pos.y+bot.pos.z*bot.pos.z))
		// == 0) ? 0 :
		// Math.asin(Math.sqrt(bot.pos.x*bot.pos.x+bot.pos.y+bot.pos.y+bot.pos.z*bot.pos.z)));

		bot.pitch = 0;// (float) (2*Math.PI-Math.asin(bot.pos.y));

		System.out.println("p: " + bot.pitch + " y:" + bot.yaw);
		int numberOfSteps = (int) ((int) 2.0 * Math
				.floor(Math.sqrt(Math.pow(rx, 2) + Math.pow(ry, 2) + Math.pow(rz, 2))));
		double sLx = rx / numberOfSteps;
		double sLy = ry / numberOfSteps;
		double sLz = rz / numberOfSteps;
		System.out.println("m: " + sLx + " " + sLy + " " + sLz + " : " + numberOfSteps);
		for (int i = 0; i < numberOfSteps; i++) {
			event.getSession().send(
					new ClientPlayerPositionRotationPacket(false, sLx + bot.pos.x, sLy + bot.pos.y, sLz + bot.pos.z,
							bot.yaw, bot.pitch));
			bot.pos.x = sLx + bot.pos.x;
			bot.pos.y = sLy + bot.pos.y;
			bot.pos.z = sLz + bot.pos.z;
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		event.getSession().send(
				new ClientPlayerPositionRotationPacket(false, ((int) bot.pos.x) + 0.5d, ((int) bot.pos.y),
						((int) bot.pos.z) + 0.5d, bot.yaw, bot.pitch));
	}

	public static Vector3d bez(Vector3d p0, Vector3d p1, int t) {
		return new Vector3d(p0.x + (p0.x - p1.x) / t, p0.y + (p0.y - p1.y) / t, p0.z + (p0.z - p1.z) / t);
	}

	public static void spline(PacketReceivedEvent event) {
		move(event, bot.positions[0].x, bot.positions[0].y, bot.positions[0].z);
		Vector3d tmp = new Vector3d(0, 0, 0);
		for (int i = 1; i < 100; i++) {
			tmp = bez(bez(bez(bot.positions[0], bot.positions[1], i), bez(bot.positions[1], bot.positions[2], i), i),
					bez(bez(bot.positions[1], bot.positions[2], i), bez(bot.positions[2], bot.positions[3], i), i), i);
			move(event, tmp.x, tmp.y, tmp.z);
		}
	}
}
