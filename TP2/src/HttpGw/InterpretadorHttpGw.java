package HttpGw;

import PDU.PacketUDP;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class InterpretadorHttpGw implements Runnable {
    private DatagramSocket socket;
    private Map<InetAddress, List<String>> ips;
    private Map<Integer, Socket> sockets;
    private Map<Integer,Boolean> sleep;
    private Queue<DatagramPacket> queue;

    public InterpretadorHttpGw(DatagramSocket socket, Map<InetAddress, List<String>> ips, Map<Integer, Socket> sockets, Map<Integer, Boolean> sleep, Queue<DatagramPacket> queue) {
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