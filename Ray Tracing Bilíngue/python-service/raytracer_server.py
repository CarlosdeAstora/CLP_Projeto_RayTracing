"""
raytracer_server.py

Servico de calculo (Python) do trabalho "Java + Python".

Este processo:
  1) Abre um servidor de sockets TCP e fica escutando conexoes.
  2) Para cada conexao, le UMA linha de texto com os parametros da cena
     (enviada pelo cliente Java).
  3) Executa o ray tracing (toda a matematica pesada fica aqui, em Python).
  4) Devolve a imagem gerada em formato PPM (binario), precedida por um
     cabecalho de 4 bytes com o tamanho da imagem em bytes.

Protocolo (ver README.md para detalhes):

  Cliente -> Servidor (texto, uma linha terminada em '\n'):
      largura,altura,esfera_x,esfera_y,esfera_z,esfera_raio,cor_r,cor_g,cor_b

  Servidor -> Cliente (binario):
      [4 bytes] tamanho N da imagem PPM, big-endian (unsigned int)
      [N bytes] conteudo do arquivo PPM (formato P6)

So usa a biblioteca padrao do Python (socket, struct, math) -- nenhuma
dependencia externa e necessaria.
"""

import socket
import struct
import math

HOST = "127.0.0.1"
PORT = 5555

# ---------------------------------------------------------------------------
# Utilidades de vetores 3D (tuplas simples (x, y, z))
# ---------------------------------------------------------------------------

def vec_add(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def vec_sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def vec_scale(a, s):
    return (a[0] * s, a[1] * s, a[2] * s)


def vec_dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def vec_length(a):
    return math.sqrt(vec_dot(a, a))


def vec_normalize(a):
    length = vec_length(a)
    if length == 0:
        return a
    return vec_scale(a, 1.0 / length)


# ---------------------------------------------------------------------------
# Cena: uma esfera + um plano de chao (xadrez) + uma luz pontual
# ---------------------------------------------------------------------------

class Sphere:
    def __init__(self, center, radius, color):
        self.center = center
        self.radius = radius
        self.color = color

    def intersect(self, origin, direction):
        """Retorna a menor distancia t > 0 ate a interseccao, ou None."""
        oc = vec_sub(origin, self.center)
        b = 2.0 * vec_dot(oc, direction)
        c = vec_dot(oc, oc) - self.radius * self.radius
        discriminant = b * b - 4.0 * c
        if discriminant < 0:
            return None
        sqrt_disc = math.sqrt(discriminant)
        t1 = (-b - sqrt_disc) / 2.0
        t2 = (-b + sqrt_disc) / 2.0
        if t1 > 1e-4:
            return t1
        if t2 > 1e-4:
            return t2
        return None


LIGHT_POS = (5.0, 8.0, -5.0)
CAMERA_POS = (0.0, 1.0, 3.0)
FLOOR_Y = -1.0
BACKGROUND_TOP = (135, 206, 250)     # azul claro
BACKGROUND_BOTTOM = (255, 255, 255)  # branco


def trace_ray(origin, direction, sphere):
    """Calcula a cor (r, g, b) [0-255] vista ao longo do raio."""

    t_sphere = sphere.intersect(origin, direction)

    # Interseccao com o plano do chao (y = FLOOR_Y), se o raio aponta para baixo.
    t_floor = None
    if direction[1] < -1e-6:
        t_floor = (FLOOR_Y - origin[1]) / direction[1]

    hit_sphere = t_sphere is not None
    hit_floor = t_floor is not None and t_floor > 1e-4

    if hit_sphere and (not hit_floor or t_sphere < t_floor):
        hit_point = vec_add(origin, vec_scale(direction, t_sphere))
        normal = vec_normalize(vec_sub(hit_point, sphere.center))
        return shade(hit_point, normal, sphere.color, sphere)

    if hit_floor:
        hit_point = vec_add(origin, vec_scale(direction, t_floor))
        # Padrao xadrez baseado nas coordenadas do ponto no plano.
        checker = (math.floor(hit_point[0]) + math.floor(hit_point[2])) % 2
        base_color = (60, 60, 65) if checker == 0 else (210, 210, 200)
        normal = (0.0, 1.0, 0.0)
        return shade(hit_point, normal, base_color, sphere, is_floor=True)

    # Nada foi atingido: fundo em degrade (ceu).
    mix = 0.5 * (direction[1] + 1.0)
    r = int(BACKGROUND_BOTTOM[0] * (1 - mix) + BACKGROUND_TOP[0] * mix)
    g = int(BACKGROUND_BOTTOM[1] * (1 - mix) + BACKGROUND_TOP[1] * mix)
    b = int(BACKGROUND_BOTTOM[2] * (1 - mix) + BACKGROUND_TOP[2] * mix)
    return (r, g, b)


def shade(hit_point, normal, base_color, sphere, is_floor=False):
    to_light = vec_sub(LIGHT_POS, hit_point)
    dist_to_light = vec_length(to_light)
    to_light = vec_normalize(to_light)

    # Raio de sombra: desloca a origem um pouco para evitar "shadow acne".
    shadow_origin = vec_add(hit_point, vec_scale(normal, 1e-3))
    in_shadow = False
    if not is_floor:
        pass
    t_shadow = sphere.intersect(shadow_origin, to_light)
    if t_shadow is not None and t_shadow < dist_to_light:
        in_shadow = True

    ambient = 0.20
    diffuse_strength = 0.0 if in_shadow else max(0.0, vec_dot(normal, to_light))

    # Especular simples (Blinn-Phong) apenas quando nao esta na sombra.
    specular = 0.0
    if not in_shadow:
        view_dir = vec_normalize(vec_sub(CAMERA_POS, hit_point))
        half_vec = vec_normalize(vec_add(to_light, view_dir))
        specular = max(0.0, vec_dot(normal, half_vec)) ** 32

    intensity = ambient + 0.75 * diffuse_strength
    r = min(255, int(base_color[0] * intensity + 255 * specular * 0.6))
    g = min(255, int(base_color[1] * intensity + 255 * specular * 0.6))
    b = min(255, int(base_color[2] * intensity + 255 * specular * 0.6))
    return (r, g, b)


def render(width, height, sphere_params):
    """Renderiza a cena inteira e devolve os bytes RGB (sem cabecalho)."""
    sx, sy, sz, sr, cr, cg, cb = sphere_params
    sphere = Sphere((sx, sy, sz), sr, (int(cr), int(cg), int(cb)))

    fov = math.pi / 3.0  # 60 graus
    aspect = width / float(height)
    tan_half_fov = math.tan(fov / 2.0)

    pixels = bytearray(width * height * 3)
    idx = 0
    for j in range(height):
        # y varia de +tan_half_fov (topo) a -tan_half_fov (base)
        y = (1.0 - 2.0 * (j + 0.5) / height) * tan_half_fov
        for i in range(width):
            x = (2.0 * (i + 0.5) / width - 1.0) * aspect * tan_half_fov
            direction = vec_normalize((x, y, -1.0))
            color = trace_ray(CAMERA_POS, direction, sphere)
            pixels[idx] = color[0]
            pixels[idx + 1] = color[1]
            pixels[idx + 2] = color[2]
            idx += 3
    return bytes(pixels)


def make_ppm(width, height, rgb_bytes):
    """Monta um arquivo PPM binario (formato P6) completo."""
    header = "P6\n{0} {1}\n255\n".format(width, height).encode("ascii")
    return header + rgb_bytes


# ---------------------------------------------------------------------------
# Servidor TCP
# ---------------------------------------------------------------------------

def handle_client(conn, addr):
    print(f"[servico-python] conexao recebida de {addr}")
    try:
        # conn.makefile facilita a leitura de uma linha de texto terminada em \n
        reader = conn.makefile("rb")
        raw_line = reader.readline()
        if not raw_line:
            return

        line = raw_line.decode("utf-8").strip()
        print(f"[servico-python] pedido: {line}")

        parts = line.split(",")
        width = int(parts[0])
        height = int(parts[1])
        sphere_params = tuple(float(p) for p in parts[2:9])

        # Limite de seguranca para nao travar o servidor com pedidos absurdos.
        width = max(16, min(width, 1600))
        height = max(16, min(height, 1200))

        rgb_bytes = render(width, height, sphere_params)
        ppm_bytes = make_ppm(width, height, rgb_bytes)

        conn.sendall(struct.pack(">I", len(ppm_bytes)))
        conn.sendall(ppm_bytes)
        print(f"[servico-python] imagem {width}x{height} enviada ({len(ppm_bytes)} bytes)")
    except Exception as exc:
        print(f"[servico-python] erro ao atender {addr}: {exc}")
    finally:
        conn.close()


def main():
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server_socket:
        server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server_socket.bind((HOST, PORT))
        server_socket.listen(5)
        print(f"[servico-python] escutando em {HOST}:{PORT} (Ctrl+C para sair)")

        while True:
            conn, addr = server_socket.accept()
            handle_client(conn, addr)


if __name__ == "__main__":
    main()
