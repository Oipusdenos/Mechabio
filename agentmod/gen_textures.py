import zlib, struct
def png(path, px, W=64, H=64):
    raw = b''.join(b'\x00' + bytes(sum((list(px[y][x]) for x in range(W)), [])) for y in range(H))
    def ch(t, d): c = struct.pack('>I', len(d)) + t + d; return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    open(path, 'wb').write(b'\x89PNG\r\n\x1a\n' + ch(b'IHDR', struct.pack('>IIBBBBB', W, H, 8, 6, 0, 0, 0)) + ch(b'IDAT', zlib.compress(raw, 9)) + ch(b'IEND', b''))
T = (0, 0, 0, 0)
skin = [[T] * 64 for _ in range(64)]
eyes = [[T] * 64 for _ in range(64)]
def h(x, y): return ((x * 73856093) ^ (y * 19349663) ^ 0x5bd1) & 0xffff
def copper(x, y):
    v = h(x, y)
    if v % 9 == 0: return (64, 181, 160, 255)    # pátina
    if v % 5 == 0: return (140, 82, 42, 255)
    if v % 7 == 0: return (214, 140, 82, 255)
    return (184, 115, 51, 255)
for (x0, y0, x1, y1) in [(0,0,32,16),(16,16,40,32),(40,16,56,32),(0,16,16,32),(32,48,48,64),(16,48,32,64)]:
    for y in range(y0, y1):
        for x in range(x0, x1): skin[y][x] = copper(x, y)
# visor + grelha no rosto (frente da cabeça: x8-15, y8-15)
for y in range(11, 14):
    for x in range(9, 15): skin[y][x] = (50, 28, 16, 255)
for x in (11, 12): skin[14][x] = (50, 28, 16, 255)
# olhos brilhantes
for x in (9, 10, 13, 14): eyes[12][x] = (255, 205, 80, 255)
# camada de placas (jaqueta, mangas, calças)
for (x0, y0, x1, y1) in [(16,32,40,48),(40,32,56,48),(48,48,64,64),(0,32,16,48),(0,48,16,64)]:
    for y in range(y0, y1):
        for x in range(x0, x1):
            if y % 6 == 0: skin[y][x] = (112, 64, 30, 255)
            elif x % 8 == 1 and y % 6 == 3: skin[y][x] = (230, 170, 110, 255)
png('src/main/resources/assets/agent/textures/entity/agent.png', skin)
png('src/main/resources/assets/agent/textures/entity/agent_eyes.png', eyes)
