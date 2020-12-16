import java.math.BigInteger;
import java.security.MessageDigest;


public class Finger {
    private String address;
    private Integer port;
    private BigInteger id;

    public Finger(String address, String port) {
        this.address = address;
        this.port = Integer.parseInt(port);
        this.id = sha256(address + port).mod(new BigInteger("2").pow(8));
    }

    public Finger(String address, Integer port) {
        this.address = address;
        this.port = port;
        this.id = sha256(address + port).mod(new BigInteger("2").pow(8));
    }

    public BigInteger getID() {
        return id;
    }

    public String getAddress() {
        return address;
    }

    public Integer getPort() {
        return port;
    }

    public static BigInteger sha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes("UTF-8"));

            return new BigInteger(hash);
        } catch (Exception e) {
            e.printStackTrace();
            return new BigInteger("0".getBytes());
        }
    }
}