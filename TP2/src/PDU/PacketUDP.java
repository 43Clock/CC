package PDU;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class PacketUDP implements Serializable {
    private int ident_Pedido;
    //private String ident_Pacote;
    private int tipo;
    private int chunk;
    private int fragmento;
    private InetAddress ip;
    private byte[] payload;

    public PacketUDP(int ident_Pedido, int tipo, int chunk, int fragmento, InetAddress ip, byte[] payload) {
        this.ident_Pedido = ident_Pedido;
        this.tipo = tipo;
        this.chunk = chunk;
        this.fragmento = fragmento;
        this.ip = ip;
        this.payload = payload;
    }

    public PacketUDP(byte[] array) throws UnknownHostException {
        this.ident_Pedido = ByteBuffer.wrap(array, 0, 4).getInt();
        this.tipo = ByteBuffer.wrap(array, 4, 4).getInt();
        this.chunk = ByteBuffer.wrap(array, 8, 4).getInt();
        this.fragmento = ByteBuffer.wrap(array, 12, 4).getInt();
        byte[] temp = new byte[4];
        System.arraycopy(array, 16, temp, 0, 4);
        this.ip = InetAddress.getByAddress(temp);
        this.payload = new byte[array.length-(4*5)];
        System.arraycopy(array, 4 * 5, this.payload, 0, array.length - (4 * 5));
    }

    public byte[] toBytes(){
        byte[] ident_pedido = intToBytes(this.ident_Pedido);
        byte[] tipo = intToBytes(this.tipo);
        byte[] chunk = intToBytes(this.chunk);
        byte[] fragmento = intToBytes(this.fragmento);

        byte[] buffer = new byte[4 * 5 + this.payload.length];

        System.arraycopy(ident_pedido, 0, buffer, 0, 4);
        System.arraycopy(tipo, 0, buffer, 4, 4);
        System.arraycopy(chunk, 0, buffer, 8, 4);
        System.arraycopy(fragmento, 0, buffer, 12, 4);
        if (this.ip != null) {
            byte[] ip = this.ip.getAddress();
            System.arraycopy(ip, 0, buffer, 16, 4);
        }
        else{
            byte[] temp = new byte[4];
            System.arraycopy(temp, 0, buffer, 16, 4);
        }
        System.arraycopy(this.payload, 0, buffer, 20, this.payload.length);

        return buffer;
    }

    private byte[] intToBytes(int i){
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt(i);
        return bb.array();
    }

    public String toString(){
        StringBuilder s = new StringBuilder();

        s.append("ID: ").append(this.ident_Pedido).append(";");
        s.append("Tipo: ").append(this.tipo).append(";");
        s.append("Chunk: ").append(this.chunk).append(";");
        s.append("Fragmento: ").append(this.fragmento).append(";");
        return s.toString();
    }

    public int getIdent_Pedido() {
        return ident_Pedido;
    }

    public int getTipo() {
        return tipo;
    }

    public int getChunk() {
        return chunk;
    }

    public int getFragmento() {
        return fragmento;
    }

    public byte[] getPayload() {
        return payload;
    }

    public InetAddress getIp() {
        return ip;
    }
}
