package HttpGw;

import PDU.PacketUDP;

import java.io.*;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RequestHandler implements Runnable {
    private Socket socket;
    private DatagramSocket datagramSocket;
    private Map<InetAddress, List<String>> ips;
    private Map<Integer,Socket> sockets;
    private Map<Integer,Boolean> sleep;
    private BufferedWriter out;
    private BufferedReader in;

    public RequestHandler(Socket socket, DatagramSocket datagramSocket, Map<InetAddress, List<String>> ips, Map<Integer, Socket> sockets, Map<Integer, Boolean> sleep) {
        this.socket = socket;
        this.datagramSocket = datagramSocket;
        this.ips = ips;
        this.sockets = sockets;
        this.sleep = sleep;
    }

    private String parseHTTP(String http) {
        String[] splited = http.split("\n");
        Pattern pattern = Pattern.compile("GET /(.+) HTTP/1\\.1");
        Matcher matcher = pattern.matcher(splited[0]);
        if (matcher.find())
            return matcher.group(1);
        else return null;
    }

    public void run() {
        try {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            //DatagramSocket datagramSocket = new DatagramSocket(8080);
            StringBuilder msg = new StringBuilder();
            String str;
            while (!(str = in.readLine()).equals("")) {
                msg.append(str).append("\n");
            }
            String file = parseHTTP(msg.toString());
            if(file != null) {
                // Ciclo para criar lista com ips que não contem o ficheiro
                List<InetAddress> ips_list = new ArrayList<>();
                for (Map.Entry<InetAddress, List<String>> a : ips.entrySet()) {
                    boolean flag = false;
                    for (String s : a.getValue()) {
                        String[] split = s.split("=");
                        if (split[0].compareTo(file) == 0) {
                            flag = true;
                            break;
                        }
                    }
                    if(!flag) ips_list.add(a.getKey());
                }

                int ident = sockets.size()+1;
                sockets.putIfAbsent(ident, socket);
                for (InetAddress ip :ips_list ) {
                    PacketUDP p = new PacketUDP(ident, 2, ips_list.size(), 1,ip, file.getBytes(StandardCharsets.UTF_8));
                    Thread worker = new Thread(new UPDSender(socket,datagramSocket,p));
                    worker.start();
                }
                if(ips_list.size()!= 0){
                    sleep.put(ident, true);
                    System.out.println("Adormeci");
                }else sleep.put(ident,false);
                while (sleep.get(ident));
                //@TODO Caso em que o ffs se desliga e fica à espera infinitamente
                //Dps sleepar

                askForFile(file,ident);


            }

        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    private void askForFile(String file, int ident) throws IOException {
        List<InetAddress> ips_list = new ArrayList<>();
        String sizeS = null;
        for (Map.Entry<InetAddress, List<String>> a : ips.entrySet()) {
            for (String s : a.getValue()) {
                String[] split = s.split("=");
                if (split[0].compareTo(file) == 0) {
                    ips_list.add(a.getKey());
                    sizeS = split[1];
                    break;
                }
            }
        }
        if(sizeS!=null) {
            long size = Long.parseLong(sizeS);
            int chunks = (int) Math.ceil((float) size / (float) PacketUDP.MAX_SIZE);
            int j = 0;
            for (int i = 0; i < chunks; i++) {
                InetAddress ip = ips_list.get(j++);
                if(j == ips_list.size()) j = 0;
                PacketUDP p = new PacketUDP(ident, 4, chunks, i+1,ip, file.getBytes(StandardCharsets.UTF_8));
                Thread worker = new Thread(new UPDSender(socket,datagramSocket,p));
                worker.start();
            }
        }
    }
}