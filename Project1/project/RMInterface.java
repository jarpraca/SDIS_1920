import java.rmi.Remote;
import java.io.IOException;

public interface RMInterface extends Remote {
    
    public void backup(String filePath, int rd) throws IOException;

    public void delete(String filePath) throws IOException;
    
    public void restore(String filePath) throws IOException;

    public void reclaim(String newCapacity) throws IOException;
    
    public void state() throws IOException;
}