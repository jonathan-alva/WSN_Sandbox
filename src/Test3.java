import com.virtenio.radio.ieee_802_15_4.Frame;
import com.virtenio.vm.Time;
import java.util.HashMap;
import com.virtenio.misc.PropertyHelper;
import com.virtenio.driver.device.at86rf231.AT86RF231;
import com.virtenio.driver.device.at86rf231.AT86RF231RadioDriver;
import com.virtenio.preon32.node.Node;
import com.virtenio.radio.ieee_802_15_4.FrameIO;
import com.virtenio.radio.ieee_802_15_4.RadioDriver;
import com.virtenio.radio.ieee_802_15_4.RadioDriverFrameIO;

public class Test3 {
	private static int COMMON_PANID = PropertyHelper.getInt("radio.panid", 0xCAFF);
	private static int[] node_list = new int[] { PropertyHelper.getInt("radio.panid", 0xABFE),
			PropertyHelper.getInt("radio.panid", 0xDAAA), PropertyHelper.getInt("radio.panid", 0xDAAB),
			PropertyHelper.getInt("radio.panid", 0xDAAC), PropertyHelper.getInt("radio.panid", 0xDAAD),
			PropertyHelper.getInt("radio.panid", 0xDAAE) };

	// ADDR_NODE1 = Node diatas
	private static int ADDR_NODE1 = node_list[2];

	// ADDR_NODE2 = Node dibawah
	// kalau tidak ada node dibawahnya = new int[0]
	private static int ADDR_NODE2[] = { PropertyHelper.getInt("radio.panid", 0xDAAD) };
//	private static int ADDR_NODE2[] = new int[0];

	// ADDR_NODE3 = Node dirinya
	private static int ADDR_NODE3 = node_list[3];
	private static int count;
	private static sensing s = new sensing();
	private static int sn = 1; // sequence number

	private static String myTemp; // Dr node sensor ke node sensor atas
//	private static String myTemp1; //
	private static String myTempEnd1; //
	private static long end; // timeout
	private static boolean isSensing = false;
	private static boolean exit = false;

//	harus bikin penyimpanan sebanyak jumlah node di bwhnya.
	private static HashMap<Integer, String> hmap = new HashMap<Integer, String>(); // penyimpanan sementara
	private static HashMap<Integer, String> hmapEnd = new HashMap<Integer, String>(); // penyimpanan sementara
	private static HashMap<Integer, Integer> hmapACK = new HashMap<Integer, Integer>();
//	private static HashMap<Integer, Integer> hmapCurr_SN = new HashMap<Integer, Integer>(); // nyimpan sn untuk setiap
	// node dibwhnya klo ada

	public static void runs() {
		try {
			AT86RF231 t = Node.getInstance().getTransceiver();
			t.open();
			t.setAddressFilter(COMMON_PANID, ADDR_NODE3, ADDR_NODE3, false);
			final RadioDriver radioDriver = new AT86RF231RadioDriver(t);
			final FrameIO fio = new RadioDriverFrameIO(radioDriver);
			send_receive(fio);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void send_receive(final FrameIO fio) throws Exception {
		Thread thread = new Thread() {
			public void run() {
				Frame frame = new Frame();
				while (true) {
					try {
						fio.receive(frame);
						byte[] dg = frame.getPayload();
						String str = new String(dg, 0, dg.length);
						// Kalau dpt yang awalan 'T' berarti isinya waktu dari node diatasnya
						// set waktu dirinya.
						if (str.charAt(0) == 'Q') {
							String tm = str.substring(1);
							long currTime = Long.parseLong(tm);
							Time.setCurrentTimeMillis(currTime);
							if (ADDR_NODE2.length > 0) {
								for (int i = 0; i < ADDR_NODE2.length; i++) {
									String message = "Q" + Time.currentTimeMillis();
									send(message, ADDR_NODE3, ADDR_NODE2[i], fio);
									Thread.sleep(50);
								}
							}
						} else if (str.charAt(0) == 'T') {
							send(str, ADDR_NODE3, ADDR_NODE3, fio);
							System.out.println(str);
						} else if (str.equalsIgnoreCase("EXIT")) {
							isSensing = false;
							exit = true;
							if (ADDR_NODE2.length > 0) {
								for (int i = 0; i < ADDR_NODE2.length; i++) {
									String message = "EXIT";
									send(message, ADDR_NODE3, ADDR_NODE2[i], fio);
									Thread.sleep(50);
								}
							}
							break;

						}
						// Kalau dpt 'WAKTU', kirim waktu dirinya ke node diatasnya, dan kirim 'WAKTU'
						// ke node di bwhnya.
						else if (str.equalsIgnoreCase("WAKTU")) {
							String msg = "Time " + Integer.toHexString(ADDR_NODE3) + " " + Time.currentTimeMillis();
							send(msg, ADDR_NODE3, ADDR_NODE1, fio);
							if (ADDR_NODE2.length > 0) {
								for (int i = 0; i < ADDR_NODE2.length; i++) {
									String message = "WAKTU";
									send(message, ADDR_NODE3, ADDR_NODE2[i], fio);
									Thread.sleep(50);
								}
							}
							System.out.println(msg);

						} // Kalau dpt 'ON' kirim status ke node diatasnya dan kirim "ON" ke node di
							// bwhnya
						else if (str.equalsIgnoreCase("ON")) {
							String msg = "Node " + Integer.toHexString(ADDR_NODE3) + " ONLINE";
							send(msg, ADDR_NODE3, ADDR_NODE1, fio);
							if (ADDR_NODE2.length > 0) {
								for (int i = 0; i < ADDR_NODE2.length; i++) {
									send("ON", ADDR_NODE3, ADDR_NODE2[i], fio);
									Thread.sleep(50);
								}
							}
							System.out.println(msg);
							// Kalau dpt akhiran 'E' (status online dr node di bwhnya) terusin ke node
							// diatasnya.
						} else if (str.charAt(str.length() - 1) == 'E') {
							send(str, ADDR_NODE3, ADDR_NODE1, fio);
						}
						// kalau dpt 'Detect', dia set end, sensing, sn++, simpen ke myTemp, kirim ke
						// node diatasnya
						// kirim juga END+ ADDR_NODE3
						// kirim 'DETECT' ke node di bwhnya
						else if (str.equalsIgnoreCase("DETECT")) {
							System.out.println("DETECT");
							end = Time.currentTimeMillis() + 4000;
							String message = "SENSE<" + ADDR_NODE3 + ">" + sn + "?" + Time.currentTimeMillis() + " "
									+ s.sense();
							myTemp = message;
							send(message, ADDR_NODE3, ADDR_NODE1, fio);
							hmap.put(ADDR_NODE3, message);
							Thread.sleep(50);
							myTempEnd1 = "END<" + sn + ">" + ADDR_NODE3;
							send(myTempEnd1, ADDR_NODE1, ADDR_NODE3, fio);
							if (ADDR_NODE2.length > 0) {
								for (int i = 0; i < ADDR_NODE2.length; i++) {
									send("DETECT", ADDR_NODE3, ADDR_NODE2[i], fio);
									Thread.sleep(50);
								}
							}
							isSensing = true;
						} else if (str.charAt(0) == 'S') {
							int startIndex = str.indexOf('<');
							int endIndex = str.indexOf('>');
							int node = Integer.parseInt(str.substring(startIndex + 1, endIndex));
							hmap.put(node, str);
							System.out.println("Receive data");
							System.out.println(str);
						} else if (str.startsWith("END")) {
							int startIndex = str.indexOf('<');
							int endIndex = str.indexOf('>');
							int node = Integer.parseInt(str.substring(endIndex + 1));
							int seq = Integer.parseInt(str.substring(startIndex + 1, endIndex));
							System.out.println("END");
							if (hmap.get(node) != null) {
								send("ACK", ADDR_NODE3, node, fio);
								hmapACK.put(node, 1);
							} else {
								send("NACK", ADDR_NODE3, node, fio);
								System.out.println("Send NACK ke bwh");
							}
						} else if (str.equalsIgnoreCase("ACK")) {
							isSensing = false;
							System.out.println("ACK");
						} else if (str.equalsIgnoreCase("NACK")) {
							System.out.println("NACK");
							send(myTemp, ADDR_NODE3, ADDR_NODE1, fio);
							System.out.println(myTemp);
							send(myTempEnd1, ADDR_NODE3, ADDR_NODE1, fio);
							System.out.println(myTempEnd1);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		};
		thread.start();

		while (thread.isAlive()) {
			if (isSensing == true && exit == false) {
				if (Time.currentTimeMillis() > end) {
					System.out.println("Timeout");

					send(myTemp, ADDR_NODE3, ADDR_NODE1, fio);
					System.out.println(myTemp);
					Thread.sleep(50);
					send(myTempEnd1, ADDR_NODE3, ADDR_NODE1, fio);
					System.out.println(myTempEnd1);
					end = Time.currentTimeMillis() + 4000;
				}
				if (hmapACK.size() == count + 1) {
					send(hmap.get(ADDR_NODE3), ADDR_NODE3, ADDR_NODE1, fio);
					for (int i = 0; i < ADDR_NODE2.length; i++) {
						send(hmap.get(ADDR_NODE2[i]), ADDR_NODE3, ADDR_NODE1, fio);
					}
					send("NODE" + hmapACK.size(), ADDR_NODE3, ADDR_NODE1, fio);
					hmapACK.clear();
					hmap.clear();
					
				}
			}
		}
	}

	public static void send(String message, int source, int destination, final FrameIO fio) {
		int frameControl = Frame.TYPE_DATA | Frame.DST_ADDR_16 | Frame.INTRA_PAN | Frame.SRC_ADDR_16;
		final Frame testFrame = new Frame(frameControl);
		testFrame.setDestPanId(COMMON_PANID);
		testFrame.setDestAddr(destination);
		testFrame.setSrcAddr(source);
		testFrame.setPayload(message.getBytes());
		try {
			fio.transmit(testFrame);
			Thread.sleep(50);
		} catch (Exception e) {
		}
	}

	public static void main(String[] args) throws Exception {
		exit = false;
		count = ADDR_NODE2.length + 1;
		runs();
	}

}
