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
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RequestHandler implements Runnable {
    private Socket socket;
    private DatagramSocket datagramSocket;
    private Map<InetAddress, List<String>> ips;
    private Map<Integer,Socket> sockets;
    private Map<Integer,Boolean> sleep;
    private ReentrantLock lock;
    private Map<Integer,Boolean> timeout;

    public RequestHandler(Socket socket, DatagramSocket datagramSocket, Map<InetAddress, List<String>> ips, Map<Integer, Socket> sockets, Map<Integer, Boolean> sleep, ReentrantLock lock, Map<Integer, Boolean> timeout) {
        this.socket = socket;
        this.datagramSocket = datagramSocket;
        this.ips = ips;
        this.sockets = sockets;
        this.sleep = sleep;
        this.lock = lock;
        this.timeout = timeout;
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
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
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

                lock.lock();
                int ident = sockets.size()+1;
                timeout.put(ident, false);
                sockets.putIfAbsent(ident, socket);

                lock.unlock();
                for (InetAddress ip :ips_list ) {
                    PacketUDP p = new PacketUDP(ident, 2, ips_list.size(), 1, file.getBytes(StandardCharsets.UTF_8));
                    Thread worker = new Thread(new SenderHttpGw(null,datagramSocket,p,null,ip));
                    worker.start();
                }
                if(ips_list.size()!= 0){
                    sleep.put(ident, true);
                }else sleep.put(ident,false);
                while (sleep.get(ident));

                //@TODO Fazer cenas quando n tem o ficheiro
                if(!timeout.get(ident)) {
                    if (!askForFile(file, ident)) {
                        Thread worker = new Thread(new SenderHttpGw(socket, null, null, new byte[0],null));
                        worker.start();
                    }
                }
            }

        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    private boolean askForFile(String file, int ident) throws IOException {
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
        if(ips_list.size() == 0){
            return false;
        }
        if(sizeS!=null) {
            long size = Long.parseLong(sizeS);
            int chunks = (int) Math.ceil((float) size / (float) PacketUDP.MAX_SIZE);
            int j = 0;
            for (int i = 0; i < chunks; i++) {
                InetAddress ip = ips_list.get(j++);
                if(j == ips_list.size()) j = 0;
                PacketUDP p = new PacketUDP(ident, 4, chunks, i+1, file.getBytes(StandardCharsets.UTF_8));
                Thread worker = new Thread(new SenderHttpGw(null,datagramSocket,p,null,ip));
                worker.start();
            }
        }
        return true;
    }
}