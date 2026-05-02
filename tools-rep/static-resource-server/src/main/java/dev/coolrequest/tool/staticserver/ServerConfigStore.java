package dev.coolrequest.tool.staticserver;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 使用 JDK 对象序列化在用户目录下保存/加载服务列表配置。
 * 文件路径: ~/.config/.cool-request/tools-data/static-resource-server/servers.dat
 */
public class ServerConfigStore {

    public static final class ServerConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        public int port;
        public String dirPath;

        public ServerConfig() {}

        public ServerConfig(int port, String dirPath) {
            this.port = port;
            this.dirPath = dirPath;
        }
    }

    private static Path storeDir() {
        String home = System.getProperty("user.home");
        return Paths.get(home, ".config", ".cool-request", "tools-data", "static-resource-server");
    }

    private static Path storeFile() {
        return storeDir().resolve("servers.dat");
    }

    @SuppressWarnings("unchecked")
    public static List<ServerConfig> load() {
        File file = storeFile().toFile();
        if (!file.isFile()) return new ArrayList<>();
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(file.toPath()))) {
            Object obj = in.readObject();
            if (obj instanceof List) {
                List<ServerConfig> result = new ArrayList<>();
                for (Object item : (List<?>) obj) {
                    if (item instanceof ServerConfig) result.add((ServerConfig) item);
                }
                return result;
            }
        } catch (IOException | ClassNotFoundException ignored) {
        }
        return new ArrayList<>();
    }

    public static void save(List<ServerConfig> configs) throws IOException {
        Path dir = storeDir();
        Files.createDirectories(dir);
        Path tmp = dir.resolve("servers.dat.tmp");
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(tmp))) {
            out.writeObject(new ArrayList<>(configs));
        }
        Files.move(tmp, storeFile(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }
}
