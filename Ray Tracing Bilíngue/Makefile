# Makefile - Ray Tracer bilingue (Java + Python)
#
# Alvos principais:
#   make build       -> compila o cliente Java (gera os .class, o "executavel")
#   make case-study   -> sobe o servico Python em background e roda o caso de
#                        estudo (HeadlessTest), que faz uma requisicao real,
#                        recebe a imagem PPM e salva um PNG de saida
#   make run          -> sobe o servico Python e abre a interface grafica (Swing)
#   make clean        -> remove os artefatos gerados (.class, PNG de teste, pid)
#
# Requisitos: JDK 17+ (javac/java) e Python 3 (python3) no PATH.

JAVA_DIR    := java-client
PYTHON_DIR  := python-service
JAVAC       := javac
JAVA        := java
PYTHON      := python3

SERVER_PID  := .raytracer_server.pid
OUTPUT_PNG  := $(JAVA_DIR)/test_output_java.png

.PHONY: all build run case-study start-server stop-server clean

all: build

# --- Geracao do "executavel" ------------------------------------------------
# Java nao gera um binario nativo: compilar (.java -> .class) e o equivalente
# a gerar o executavel, que depois roda via "java NomeDaClasse".
build:
	$(JAVAC) $(JAVA_DIR)/RayTracerClient.java $(JAVA_DIR)/HeadlessTest.java
	@echo "Build concluido: classes .java compiladas em $(JAVA_DIR)/"

# --- Servico Python (backend de calculo) ------------------------------------
start-server:
	@echo "Iniciando servico Python (raytracer_server.py) em background..."
	@$(PYTHON) $(PYTHON_DIR)/raytracer_server.py & echo $$! > $(SERVER_PID)
	@sleep 1

stop-server:
	@if [ -f $(SERVER_PID) ]; then \
		kill `cat $(SERVER_PID)` 2>/dev/null || true; \
		rm -f $(SERVER_PID); \
		echo "Servico Python encerrado."; \
	fi

# --- Caso de estudo (execucao de referencia, sem interface grafica) --------
# Sobe o servidor Python, roda o cliente HeadlessTest (que envia um pedido de
# exemplo fixo, recebe a imagem PPM e grava um PNG) e depois derruba o servidor.
case-study: build start-server
	@echo "Executando caso de estudo (HeadlessTest, sem interface grafica)..."
	-$(JAVA) -Djava.awt.headless=true -cp $(JAVA_DIR) HeadlessTest
	@$(MAKE) --no-print-directory stop-server
	@echo "Caso de estudo concluido. Saida: $(OUTPUT_PNG)"

# --- Execucao completa com interface grafica --------------------------------
run: build start-server
	@echo "Abrindo interface grafica Java (RayTracerClient)..."
	-$(JAVA) -cp $(JAVA_DIR) RayTracerClient
	@$(MAKE) --no-print-directory stop-server

clean:
	rm -f $(JAVA_DIR)/*.class
	rm -f $(OUTPUT_PNG)
	rm -f $(SERVER_PID)
	@echo "Artefatos removidos."
