package HttpGw;

import PDU.PacketUDP;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.Math.max;

public class InterpretadorHttpGw implements Runnable {
    private DatagramSocket socket;
    private Map<InetAddress, List<String>> ips;
    private Map<Integer, Socket> sockets;
    private Map<Integer,Boolean> sleep;
    private Queue<DatagramPacket> queue;
    private ReentrantLock lock;
    private Map<Integer,Boolean> timedOut;

    public InterpretadorHttpGw(DatagramSocket socket, Map<InetAddress, List<String>> ips, Map<Integer, Socket> sockets, Map<Integer, Boolean> sleep, Queue<DatagramPacket> queue, ReentrantLock lock, Map<Integer, Boolean> timedOut) {
        this.socket = socket;
        this.ips = ips;
        this.sockets = sockets;
        this.sleep = sleep;
        this.queue = queue;
        this.lock = lock;
        this.timedOut = timedOut;
    }

    public void run(){
        try {
            while (true) {
                while (queue.peek() == null) ;
                lock.lock();
                    DatagramPacket packet = queue.remove();
                lock.unlock();

                byte[] result = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), 0, result, 0, packet.getLength());
                PacketUDP received = new PacketUDP(result);
                switch (received.getTipo()) {
                    case 1:
                        ips.put(packet.getAddress(), new ArrayList<>());
                        System.out.println("Ip adicionado");
                        break;
                    case 3:
                        timedOut.put(received.getIdent_Pedido(),false);
                        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
                        executor.schedule(new TimeOutRequest(received.getIdent_Pedido(),timedOut), 10, TimeUnit.SECONDS);

                        if (received.getPayload().length != 0) {
                            String file = new String(received.getPayload(), StandardCharsets.UTF_8);
                            ips.get(packet.getAddress()).add(file);
                        }
                        int rec = 1;
                        while (rec < received.getChunk() && !timedOut.get(received.getIdent_Pedido())) {
                            while (queue.peek()==null);//Fica à espera se não houver pacotes na queue
                            boolean flag = false;
                            DatagramPacket temp;
                            PacketUDP received2;
                            while (!flag && !timedOut.get(received.getIdent_Pedido())) {
                                lock.lock();
                                    temp = queue.remove();
                                lock.unlock();
                                byte[] result2 = new byte[temp.getLength()];
                                System.arraycopy(temp.getData(), 0, result2, 0, temp.getLength());
                                received2 = new PacketUDP(result2);
                                if(received2.getIdent_Pedido() == received.getIdent_Pedido() && received2.getTipo() == received.getTipo()){
                                    flag = true;
                                    rec++;
                                    if (received2.getPayload().length != 0) {
                                        String file = new String(received2.getPayload(), StandardCharsets.UTF_8);
                                        ips.get(temp.getAddress()).add(file);
                                    }
                                }
                                else{
                                    lock.lock();
                                        queue.add(temp);
                                    lock.unlock();
                                }
                            }

                        }
                        executor.shutdownNow();
                        if(timedOut.get(received.getIdent_Pedido())){
                            Thread worker2 = new Thread(new SenderHttpGw(sockets.get(received.getIdent_Pedido()), socket, null, null,packet.getAddress()));
                            worker2.start();
                        }

                        sleep.put(received.getIdent_Pedido(),false);
                        break;
                    case 5:
                        //TimeOutHandler
                        timedOut.put(received.getIdent_Pedido(),false);
                        ScheduledExecutorService executor2 = Executors.newSingleThreadScheduledExecutor();
                        int time = max(10,(received.getChunk()/50));
                        executor2.schedule(new TimeOutRequest(received.getIdent_Pedido(),timedOut), time, TimeUnit.SECONDS);

                        //Send Ack
                        PacketUDP p = new PacketUDP(received.getIdent_Pedido(), 6, received.getChunk(), received.getFragmento(), new byte[0]);
                        Thread worker = new Thread(new SenderHttpGw(null,socket,p,null,packet.getAddress()));
                        worker.start();

                        int sizeOfLastPayload = 0;
                        byte[] reconstruct = new byte[PacketUDP.MAX_SIZE* received.getChunk()];
                        if(received.getChunk() == received.getFragmento()){
                            sizeOfLastPayload = received.getPayload().length;
                            System.arraycopy(received.getPayload(),0,reconstruct,PacketUDP.MAX_SIZE*(received.getChunk()-1),sizeOfLastPayload);
                        }else{
                            System.arraycopy(received.getPayload(),0,reconstruct,PacketUDP.MAX_SIZE*(received.getFragmento()-1),PacketUDP.MAX_SIZE);
                        }
                        int rec2 = 1;
                        while (rec2 < received.getChunk() && !timedOut.get(received.getIdent_Pedido())) {
                            while (queue.peek()==null);//Fica à espera se não houver pacotes na queue
                            boolean flag = false;
                            DatagramPacket temp;
                            PacketUDP received2;
                            while (!flag && !timedOut.get(received.getIdent_Pedido())) {
                                lock.lock();
                                    temp = queue.remove();
                                lock.unlock();
                                byte[] result2 = new byte[temp.getLength()];
                                System.arraycopy(temp.getData(), 0, result2, 0, temp.getLength());
                                received2 = new PacketUDP(result2);
                                if(received2.getIdent_Pedido() == received.getIdent_Pedido() && received2.getTipo() == received.getTipo()){
                                    //Send ack
                                    PacketUDP p2 = new PacketUDP(received2.getIdent_Pedido(), 6, received2.getChunk(), received2.getFragmento(), new byte[0]);
                                    Thread worker2 = new Thread(new SenderHttpGw(null,socket,p2,null,temp.getAddress()));
                                    worker2.start();

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
                                    lock.lock();
                                        queue.add(temp);
                                    lock.unlock();
                                }
                            }
                        }
                        executor2.shutdownNow();
                        if(!timedOut.get(received.getIdent_Pedido())) {
                            reconstruct = Arrays.copyOfRange(reconstruct, 0, PacketUDP.MAX_SIZE * (received.getChunk() - 1) + sizeOfLastPayload);
                            Thread worker2 = new Thread(new SenderHttpGw(sockets.get(received.getIdent_Pedido()), null, null, reconstruct,packet.getAddress()));
                            worker2.start();
                        }else {
                            Thread worker2 = new Thread(new SenderHttpGw(sockets.get(received.getIdent_Pedido()), socket, null, null,packet.getAddress()));
                            worker2.start();
                        }
                        break;
                    case 7:
                        InetAddress ip = packet.getAddress();
                        ips.remove(ip);
                        System.out.println("Ip Removido");
                        break;
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

class TimeOutRequest implements Runnable {
    private int ident;
    private Map<Integer,Boolean> timeout;


    public TimeOutRequest(int ident, Map<Integer, Boolean> timeout) {
        this.ident = ident;
        this.timeout = timeout;
    }

    public void run(){
        System.out.println("TIMEOUT");
        timeout.put(ident, true);
    }
}

