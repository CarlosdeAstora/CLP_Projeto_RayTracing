import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * RayTracerClient
 *
 * Interface grafica (Java) do trabalho "Java + Python".
 *
 * Esta classe NAO faz nenhum calculo de ray tracing. Sua unica
 * responsabilidade e:
 *   1) Coletar os parametros da cena digitados pelo usuario;
 *   2) Enviar esses parametros ao servico Python via socket TCP;
 *   3) Receber a imagem (formato PPM) gerada pelo Python;
 *   4) Decodificar o PPM manualmente e exibir na tela com Swing.
 *
 * Todo o calculo pesado (o ray tracer em si) esta em
 * python-service/raytracer_server.py.
 */
public class RayTracerClient extends JFrame {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 5555;

    private final JTextField widthField = new JTextField("400", 5);
    private final JTextField heightField = new JTextField("300", 5);
    private final JTextField sphereXField = new JTextField("0", 4);
    private final JTextField sphereYField = new JTextField("0", 4);
    private final JTextField sphereZField = new JTextField("-3", 4);
    private final JTextField sphereRField = new JTextField("1.2", 4);
    private final JTextField colorRField = new JTextField("200", 4);
    private final JTextField colorGField = new JTextField("60", 4);
    private final JTextField colorBField = new JTextField("60", 4);

    private final JLabel imageLabel = new JLabel();
    private final JLabel statusLabel = new JLabel("Pronto. Clique em \"Renderizar\".");
    private final JButton renderButton = new JButton("Renderizar");

    public RayTracerClient() {
        super("Ray Tracer - Interface Java  |  Calculo em Python");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        add(buildControlPanel(), BorderLayout.NORTH);

        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setText("(nenhuma imagem ainda)");
        JScrollPane scrollPane = new JScrollPane(imageLabel);
        add(scrollPane, BorderLayout.CENTER);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(statusLabel, BorderLayout.SOUTH);

        setSize(760, 620);
        setLocationRelativeTo(null);
    }

    private JPanel buildControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));

        panel.add(new JLabel("Largura:"));
        panel.add(widthField);
        panel.add(new JLabel("Altura:"));
        panel.add(heightField);

        panel.add(new JLabel("   Esfera - centro (x, y, z) e raio:"));
        panel.add(sphereXField);
        panel.add(sphereYField);
        panel.add(sphereZField);
        panel.add(sphereRField);

        panel.add(new JLabel("   Cor (R,G,B):"));
        panel.add(colorRField);
        panel.add(colorGField);
        panel.add(colorBField);

        renderButton.addActionListener(this::onRenderClicked);
        panel.add(renderButton);

        return panel;
    }

    private void onRenderClicked(ActionEvent event) {
        renderButton.setEnabled(false);
        statusLabel.setText("Enviando pedido ao servico Python e renderizando...");

        // Trabalho de rede/IO roda fora da Event Dispatch Thread para nao travar a interface.
        SwingWorker<BufferedImage, Void> worker = new SwingWorker<>() {
            @Override
            protected BufferedImage doInBackground() throws Exception {
                return requestImageFromPythonService();
            }

            @Override
            protected void done() {
                renderButton.setEnabled(true);
                try {
                    BufferedImage image = get();
                    imageLabel.setText(null);
                    imageLabel.setIcon(new ImageIcon(image));
                    statusLabel.setText("Concluido: imagem " + image.getWidth() + "x" + image.getHeight()
                            + " recebida do servico Python.");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setText("Erro: " + cause.getMessage());
                    JOptionPane.showMessageDialog(RayTracerClient.this,
                            "Falha ao comunicar com o servico Python em " + HOST + ":" + PORT
                                    + ".\n\nVerifique se o servidor 'raytracer_server.py' esta em execucao.\n\n"
                                    + "Detalhe: " + cause,
                            "Erro de comunicacao", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    /**
     * Abre uma conexao TCP com o servico Python, envia os parametros da cena
     * em uma unica linha de texto e le de volta a imagem PPM gerada.
     */
    private BufferedImage requestImageFromPythonService() throws IOException {
        String request = buildRequestLine();

        try (Socket socket = new Socket(HOST, PORT)) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            out.writeBytes(request + "\n");
            out.flush();

            int length = in.readInt();          // cabecalho: 4 bytes, tamanho da imagem
            byte[] ppmData = new byte[length];
            in.readFully(ppmData);              // corpo: bytes do arquivo PPM

            return decodePPM(ppmData);
        }
    }

    private String buildRequestLine() {
        return String.join(",",
                widthField.getText().trim(),
                heightField.getText().trim(),
                sphereXField.getText().trim(),
                sphereYField.getText().trim(),
                sphereZField.getText().trim(),
                sphereRField.getText().trim(),
                colorRField.getText().trim(),
                colorGField.getText().trim(),
                colorBField.getText().trim()
        );
    }

    /**
     * Decodifica manualmente um arquivo PPM binario (formato P6):
     *   linha 1: "P6"
     *   linha 2: "<largura> <altura>"
     *   linha 3: "<valor maximo>" (255)
     *   em seguida: bytes RGB brutos, 3 bytes por pixel, sem separadores.
     */
    private BufferedImage decodePPM(byte[] data) throws IOException {
        int pos = 0;
        int newlinesFound = 0;
        StringBuilder header = new StringBuilder();

        while (newlinesFound < 3) {
            char c = (char) (data[pos++] & 0xFF);
            header.append(c);
            if (c == '\n') {
                newlinesFound++;
            }
        }

        String[] tokens = header.toString().trim().split("\\s+");
        if (tokens.length < 4 || !tokens[0].equals("P6")) {
            throw new IOException("Formato PPM invalido recebido do servico Python.");
        }
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new RayTracerClient().setVisible(true));
    }
}
