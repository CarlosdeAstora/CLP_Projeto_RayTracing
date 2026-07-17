# Ray Tracer Bilíngue — Java + Python

## 📖 Resumo do projeto

Este projeto é um ray tracer simples dividido em dois processos que se comunicam por
rede: uma **interface gráfica em Java** (Swing) e um **serviço de cálculo em Python**.
O usuário define os parâmetros da cena (resolução da imagem, posição/raio de uma
esfera e sua cor) pela interface Java, que envia esse pedido ao serviço Python via
socket TCP. O Python executa todo o cálculo do ray tracing — interseção raio-esfera,
interseção com o chão, sombras e iluminação difusa/especular — e devolve a imagem
renderizada, que é então exibida na janela Java.

O foco do trabalho não é o ray tracer em si, mas demonstrar a integração entre duas
linguagens com propósitos diferentes, cada uma responsável por uma camada distinta da
aplicação (interface × processamento), comunicando-se como cliente e servidor.

## 👥 Integrantes do grupo

- Carlos Henrique Carvalho
- Vinicius Lempek
- Douglas Garcia

## 🛠️ Tecnologias usadas

- **Java (Swing)** — interface gráfica e cliente TCP
- **Python 3** — serviço de cálculo (ray tracing) e servidor TCP
- **Sockets TCP** (`java.net` / `socket` da biblioteca padrão) — comunicação entre as duas linguagens
- **Formato PPM (P6)** — formato de imagem usado na resposta do servidor
- **Make** — automação de build e execução

## ▶️ Como executar

**Pré-requisitos:** JDK 17+ e Python 3 instalados e disponíveis no PATH.

### Usando o Makefile (recomendado)

```bash
make build         # compila o cliente Java
make run            # sobe o serviço Python e abre a interface gráfica
make case-study      # sobe o serviço Python e roda um caso de estudo sem interface gráfica
```

### Manualmente

1. Suba o serviço Python (deixe rodando em um terminal):

   ```bash
   cd python-service
   python3 raytracer_server.py
   ```

2. Em outro terminal, compile e execute o cliente Java:

   ```bash
   cd java-client
   javac RayTracerClient.java
   java RayTracerClient
   ```

3. Ajuste os parâmetros da cena na janela e clique em **"Renderizar"**.
