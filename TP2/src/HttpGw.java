import PDU.PacketUDP;

import javax.xml.crypto.Data;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


class RequestHandler implements Runnable {
    private Socket socket;
    private DatagramSocket datagramSocket;
    private Map<InetAddress,List<String>> ips;
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

class Interpretador implements Runnable {
    private DatagramSocket socket;
    private Map<InetAddress, List<String>> ips;
    private Map<Integer, Socket> sockets;
    private Map<Integer,Boolean> sleep;
    private Queue<DatagramPacket> queue;

    public Interpretador(DatagramSocket socket, Map<InetAddress, List<String>> ips, Map<Integer, Socket> sockets, Map<Integer, Boolean> sleep, Queue<DatagramPacket> queue) {
        this.socket = socket;
        this.ips = ips;
        this.sockets = sockets;
        this.sleep = sleep;
        this.queue = queue;
    }

    public void run(){
        try {
            while (true) {
                while (queue.peek() == null) ;
                DatagramPacket packet = queue.remove();
                byte[] result = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), 0, result, 0, packet.getLength());
                PacketUDP received = new PacketUDP(result);
                switch (received.getTipo()) {
                    case 1:
                        ips.put(packet.getAddress(), new ArrayList<>());
                        System.out.println("Ip adicionado");
                        break;
                    case 3:
                        if (received.getPayload().length != 0) {
                            String file = new String(received.getPayload(), StandardCharsets.UTF_8);
                            ips.get(received.getIp()).add(file);
                        }
                        int rec = 1;
                        while (rec < received.getChunk()) {
                            while (queue.peek()==null);//Fica à espera se não houver pacotes na queue
                            boolean flag = false;
                            DatagramPacket temp;
                            PacketUDP received2;
                            while (!flag) {
                                temp = queue.remove();
                                byte[] result2 = new byte[temp.getLength()];
                                System.arraycopy(temp.getData(), 0, result2, 0, temp.getLength());
                                received2 = new PacketUDP(result2);
                                if(received2.getIdent_Pedido() == received.getIdent_Pedido() && received2.getTipo() == received.getTipo()){
                                    flag = true;
                                    rec++;
                                }
                                else{
                                    queue.add(temp);
                                }
                            }
                            if (received.getPayload().length != 0) {
                                String file = new String(received.getPayload(), StandardCharsets.UTF_8);
                                ips.get(received.getIp()).add(file);
                            }
                        }
                        sleep.put(received.getIdent_Pedido(),false);
                        break;
                    case 5:
                        int sizeOfLastPayload = 0;
                        byte[] reconstruct = new byte[PacketUDP.MAX_SIZE* received.getChunk()];
                        if(received.getChunk() == received.getFragmento()){
                            sizeOfLastPayload = received.getPayload().length;
                            System.arraycopy(received.getPayload(),0,reconstruct,PacketUDP.MAX_SIZE*(received.getChunk()-1),sizeOfLastPayload);
                        }else{
                            System.arraycopy(received.getPayload(),0,reconstruct,PacketUDP.MAX_SIZE*(received.getFragmento()-1),PacketUDP.MAX_SIZE);
                        }
                        int rec2 = 1;
                        while (rec2 < received.getChunk()) {
                            while (queue.peek()==null);//Fica à espera se não houver pacotes na queue
                            boolean flag = false;
                            DatagramPacket temp;
                            PacketUDP received2;
                            while (!flag) {
                                temp = queue.remove();
                                byte[] result2 = new byte[temp.getLength()];
                                System.arraycopy(temp.getData(), 0, result2, 0, temp.getLength());
                                received2 = new PacketUDP(result2);
                                if(received2.getIdent_Pedido() == received.getIdent_Pedido() && received2.getTipo() == received.getTipo()){
                                    if(received2.getChunk() == received2.getFragmento()){
                                        sizeOfLastPayload = received2.getPayload().length;
                                        System.arraycopy(received2.getPayload(),0,reconstruct,PacketUDP.MAX_SIZE*(received2.getChunk()-1),sizeOfLastPayload);
                                    }else {
                                        System.arraycopy(received2.getPayload(),0,reconstruct,PacketUDP.MAX_SIZE*(received2.getFragmento()-1),PacketUDP.MAX_SIZE);
                                    }
                                    flag = true;
                                    rec2++;
                                }
                                else{
                                    queue.add(temp);
                                }
                            }
                        }
                        System.out.println(sizeOfLastPayload);
                        reconstruct = Arrays.copyOfRange(reconstruct,0,PacketUDP.MAX_SIZE*(received.getChunk()-1)+sizeOfLastPayload);
                        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(sockets.get(received.getIdent_Pedido()).getOutputStream()));
                        out.write(reconstruct);
                        out.flush();
                        sockets.get(received.getIdent_Pedido()).close();
                        break;
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class UDPListener implements Runnable {
    private DatagramSocket datagramSocket;
    /*private Map<InetAddress, List<String>> ips;
    private Map<Integer, Socket> sockets;
    private Map<Integer,Integer> num_pedidos;
    private Map<Integer,Boolean> sleep;*/
    private Queue<DatagramPacket> queue;


    public UDPListener(DatagramSocket datagramSocket, Queue<DatagramPacket> queue) {
        this.datagramSocket = datagramSocket;
        this.queue = queue;
    }

    public void run() {
        try {
            while (true) {
                byte[] pBytes_received = new byte[PacketUDP.MAX_SIZE+200];
                DatagramPacket packet = new DatagramPacket(pBytes_received, pBytes_received.length);
                datagramSocket.receive(packet);
                queue.add(packet);
            }
        } catch (IOException e) {
            System.out.println("Erro: "+e.getMessage());
        }
    }
}

class UPDSender implements Runnable {
    private Socket socket;
    private DatagramSocket datagramSocket;
    private PacketUDP received;

    public UPDSender(Socket socket, DatagramSocket datagramSocket, PacketUDP received) throws IOException {
        this.socket = socket;
        this.datagramSocket = datagramSocket;
        this.received = received;
    }

    public void run(){
        //Aqui vê qual é o tipo do pacote e faz cenas com isso

        try {
            InetAddress address = InetAddress.getByName("localhost");
            byte[] pBytes = received.toBytes();
            if (received.getTipo() == 3) {
                System.out.println("Cliente");
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                String aux = new String(received.getPayload(), StandardCharsets.UTF_8);
                out.writeBytes(aux);
                out.flush();
                socket.close();
            }
            else {
                DatagramPacket packet = new DatagramPacket(pBytes, pBytes.length,address,8880);
                datagramSocket.send(packet);
            }


        } catch (IOException e) {
            e.printStackTrace();
        }


    }
}



public class HttpGw {
    public static void main(String[] args) throws IOException {
        ServerSocket ss = new ServerSocket(8080);
        DatagramSocket datagramSocket = new DatagramSocket(8888);
        Map<InetAddress, List<String>> map = new HashMap<>();
        Map<Integer,Socket> sockets = new HashMap<>();
        Map<Integer, Boolean> sleep = new HashMap<>();
        Queue<DatagramPacket> queue = new LinkedBlockingDeque<>();
        Thread listener = new Thread(new UDPListener(datagramSocket, queue));
        listener.start();
        Thread interpretador = new Thread(new Interpretador(datagramSocket,map,sockets,sleep,queue));
        interpretador.start();

        while (true) {
            Socket socket = ss.accept();
            Thread worker = new Thread(new RequestHandler(socket,datagramSocket, map,sockets,sleep));
            worker.start();
        }
    }
}
