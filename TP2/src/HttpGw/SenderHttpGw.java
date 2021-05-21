package HttpGw;

import PDU.PacketUDP;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class SenderHttpGw implements Runnable {
    private Socket socket;
    private DatagramSocket datagramSocket;
    private PacketUDP received;
    private byte[] dados;

    public SenderHttpGw(Socket socket, DatagramSocket datagramSocket, PacketUDP received, byte[] dados) {
        this.socket = socket;
        this.datagramSocket = datagramSocket;
        this.received = received;
        this.dados = dados;
    }

    public void run(){

        try {
            if (socket != null) {
                PrintWriter out = new PrintWriter(socket.getOutputStream());
                BufferedOutputStream outData = new BufferedOutputStream(socket.getOutputStream());
                if(dados.length != 0 && datagramSocket==null){
                    out.println("HTTP/1.1 200 OK");
                    out.println("Server: Java HTTP Server from HttpGw : 1.0");
                    out.println("Date: " + new Date());
                    out.println("Content-length: " + dados.length);
                    out.println();
                    out.flush();

                    outData.write(dados, 0, dados.length);
                    outData.flush();
                }else if(datagramSocket == null){
                    out.println("HTTP/1.1 404 File not Found");
                    out.println("Server: Java HTTP Server from HttpGw : 1.0");
                    out.println("Date: " + new Date());
                    out.println();
                    out.flush();
                }
                else {
                    out.println("HTTP/1.1 408 Request Timeout");
                    out.println("Server: Java HTTP Server from HttpGw : 1.0");
                    out.println("Date: " + new Date());
                    out.println();
                    out.flush();
                }

               //socket.close();
            }
            else {
                byte[] pBytes = received.toBytes();
                DatagramPacket packet = new DatagramPacket(pBytes, pBytes.length,received.getIp(),8888);
                datagramSocket.send(packet);
            }


        } catch (IOException e) {
            e.printStackTrace();
        }


    }
}