import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.net.Socket;

/**
 * Teste sem interface grafica: conecta no servico Python, pede uma imagem,
 * decodifica o PPM e salva como PNG (usando ImageIO), so para validar
 * a comunicacao entre as duas linguagens sem precisar de um display.
 */
public class HeadlessTest {
    public static void main(String[] args) throws Exception {
        String request = "400,300,0,0,-3,1.2,60,120,220\n"; // esfera azulada, so para variar

        try (Socket socket = new Socket("127.0.0.1", 5555)) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            out.writeBytes(request);
            out.flush();

            int length = in.readInt();
            System.out.println("tamanho anunciado pelo servico Python: " + length);
            byte[] data = new byte[length];
            in.readFully(data);
            System.out.println("bytes recebidos: " + data.length);

            BufferedImage image = decodePPM(data);
            System.out.println("imagem decodificada: " + image.getWidth() + "x" + image.getHeight());

            ImageIO.write(image, "png", new File("/home/claude/test_output_java.png"));
            System.out.println("salvo em /home/claude/test_output_java.png");
        }
    }

    private static BufferedImage decodePPM(byte[] data) throws Exception {
        int pos = 0;
        int newlinesFound = 0;
        StringBuilder header = new StringBuilder();
        while (newlinesFound < 3) {
            char c = (char) (data[pos++] & 0xFF);
            header.append(c);
            if (c == '\n') newlinesFound++;
        }
        String[] tokens = header.toString().trim().split("\\s+");
        int width = Integer.parseInt(tokens[1]);
        int height = Integer.parseInt(tokens[2]);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int idx = pos;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = data[idx++] & 0xFF;
                int g = data[idx++] & 0xFF;
                int b = data[idx++] & 0xFF;
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }
}
