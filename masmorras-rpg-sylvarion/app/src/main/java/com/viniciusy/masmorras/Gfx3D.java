package com.viniciusy.masmorras;

/**
 * Renderizador de malhas LISAS (elipsoides, caixas arredondadas, tubos/lofts, tecido) por software.
 * Java puro, sem dependência de Android: dá para testar numa JVM comum.
 *
 * Câmera: ortográfica inclinada (mesma do jogo). Mundo: x direita, y cima, z frente.
 *   tela x = ox + wx*rs ; tela y = oy + (wz*SINP - wy*COSP)*rs ; profundidade = wy*SINP + wz*COSP (maior = mais perto)
 *
 * Uso por quadro: begin() -> (push/translate/rot... + primitivas) -> render() -> ler pix/outW/outH/bx0/by0/ss.
 */
public final class Gfx3D {
    // ───────── câmera ─────────
    public float pitch = 0.8f;
    public float rs = 1f, ox = 0f, oy = 0f;
    private float SINP = (float) Math.sin(0.8), COSP = (float) Math.cos(0.8);

    // ───────── materiais ─────────
    public static final int M_CLOTH = 0, M_SKIN = 1, M_METAL = 2, M_LEATHER = 3, M_HAIR = 4, M_GLOW = 5, M_WOOD = 6;
    private static final float[] SPEC = {0.00f, 0.10f, 0.85f, 0.18f, 0.35f, 0f, 0.22f};
    private static final float[] SHIN = {8f, 14f, 38f, 12f, 22f, 8f, 16f};
    private static final float[] WRAP = {0.55f, 0.65f, 0.35f, 0.5f, 0.5f, 0.5f, 0.5f};

    // ───────── pilha de matrizes (9 de rotação linha a linha + 3 de translação) ─────────
    private final float[][] st = new float[24][12];
    private int top = 0;
    public int mat = M_CLOTH;
    public boolean outline = true;   // contorno nas próximas primitivas
    public float bias = 0f;          // viés de profundidade nas próximas primitivas

    public void identity() {
        float[] m = st[top];
        for (int i = 0; i < 12; i++) m[i] = 0f;
        m[0] = 1f; m[4] = 1f; m[8] = 1f;
    }
    public void push() { System.arraycopy(st[top], 0, st[top + 1], 0, 12); top++; }
    public void pop() { top--; }
    public float[] cur() { return st[top]; }
    public void translate(float x, float y, float z) {
        float[] m = st[top];
        m[9] += m[0] * x + m[1] * y + m[2] * z;
        m[10] += m[3] * x + m[4] * y + m[5] * z;
        m[11] += m[6] * x + m[7] * y + m[8] * z;
    }
    private void mix(int i, int j, float a, float b, float c, float d) {
        float[] m = st[top];
        for (int r = 0; r < 3; r++) {
            float ci = m[r * 3 + i], cj = m[r * 3 + j];
            m[r * 3 + i] = a * ci + b * cj;
            m[r * 3 + j] = c * ci + d * cj;
        }
    }
    public void scale(float x, float y, float z) {
        float[] m = st[top];
        for (int r = 0; r < 3; r++) { m[r * 3] *= x; m[r * 3 + 1] *= y; m[r * 3 + 2] *= z; }
    }
    public void rotX(float a) { float c = (float) Math.cos(a), s = (float) Math.sin(a); mix(1, 2, c, s, -s, c); }
    public void rotY(float a) { float c = (float) Math.cos(a), s = (float) Math.sin(a); mix(0, 2, c, -s, s, c); }
    public void rotZ(float a) { float c = (float) Math.cos(a), s = (float) Math.sin(a); mix(0, 1, c, s, -s, c); }
    /** multiplica por uma base (colunas X, Y, Z) */
    public void basis(float[] b) {
        float[] m = st[top];
        for (int r = 0; r < 3; r++) {
            float a0 = m[r * 3], a1 = m[r * 3 + 1], a2 = m[r * 3 + 2];
            m[r * 3] = a0 * b[0] + a1 * b[1] + a2 * b[2];
            m[r * 3 + 1] = a0 * b[3] + a1 * b[4] + a2 * b[5];
            m[r * 3 + 2] = a0 * b[6] + a1 * b[7] + a2 * b[8];
        }
    }
    /** ponto local -> mundo (resultado em wx, wy, wz) */
    public float wx, wy, wz;
    public void toWorld(float x, float y, float z) {
        float[] m = st[top];
        wx = m[0] * x + m[1] * y + m[2] * z + m[9];
        wy = m[3] * x + m[4] * y + m[5] * z + m[10];
        wz = m[6] * x + m[7] * y + m[8] * z + m[11];
    }

    // ───────── buffers de malha ─────────
    private static final int VMAX = 90000, TMAX = 150000, PMAX = 3000;
    private final float[] vX = new float[VMAX], vY = new float[VMAX], vD = new float[VMAX];
    private final float[] vNx = new float[VMAX], vNy = new float[VMAX], vNz = new float[VMAX];
    private int nv = 0;
    private final int[] tA = new int[TMAX], tB = new int[TMAX], tC = new int[TMAX];
    private final int[] tP = new int[TMAX];
    private int nt = 0;
    private final int[] pCol = new int[PMAX];
    private final byte[] pMat = new byte[PMAX];
    private final boolean[] pOut = new boolean[PMAX];
    private final boolean[] pDbl = new boolean[PMAX];
    private final float[] pBias = new float[PMAX];
    private int np = 0;

    public int polyCount() { return nt; }

    // ───────── resultado ─────────
    public int[] pix = new int[0];
    public int outW, outH;
    public float bx0, by0, ss = 1f;
    private float[] zb = new float[0];
    private int[] ib = new int[0];
    private float[] nbx = new float[0], nby = new float[0], nbz = new float[0];

    public void begin(float rsNew, float oxNew, float oyNew, float pitchNew) {
        rs = rsNew; ox = oxNew; oy = oyNew;
        if (pitchNew != pitch) {
            pitch = pitchNew;
            SINP = (float) Math.sin(pitch);
            COSP = (float) Math.cos(pitch);
        }
        top = 0; nv = 0; nt = 0; np = 0;
        identity();
        mat = M_CLOTH; outline = true; bias = 0f;
    }

    // ───────── gabaritos (superelipsoides) ─────────
    private static final class Tmpl {
        final int nVert; final float[] px, py, pz, qx, qy, qz; final int[] tri;
        Tmpl(float e, int lon, int lat) {
            int rows = lat + 1, cols = lon;
            nVert = rows * cols;
            px = new float[nVert]; py = new float[nVert]; pz = new float[nVert];
            qx = new float[nVert]; qy = new float[nVert]; qz = new float[nVert];
            for (int i = 0; i <= lat; i++) {
                double ph = -Math.PI / 2 + Math.PI * i / lat;
                double cp = Math.cos(ph), sp = Math.sin(ph);
                for (int j = 0; j < lon; j++) {
                    double th = 2 * Math.PI * j / lon;
                    double ct = Math.cos(th), stt = Math.sin(th);
                    int k = i * cols + j;
                    px[k] = (float) (spow(cp, e) * spow(ct, e));
                    py[k] = (float) spow(sp, e);
                    pz[k] = (float) (spow(cp, e) * spow(stt, e));
                    qx[k] = (float) (spow(cp, 2 - e) * spow(ct, 2 - e));
                    qy[k] = (float) spow(sp, 2 - e);
                    qz[k] = (float) (spow(cp, 2 - e) * spow(stt, 2 - e));
                }
            }
            int[] tr = new int[lat * lon * 6];
            int n = 0;
            for (int i = 0; i < lat; i++) {
                for (int j = 0; j < lon; j++) {
                    int j2 = (j + 1) % lon;
                    int a = i * cols + j, b = i * cols + j2, c = (i + 1) * cols + j, d = (i + 1) * cols + j2;
                    tr[n++] = a; tr[n++] = b; tr[n++] = d;
                    tr[n++] = a; tr[n++] = d; tr[n++] = c;
                }
            }
            tri = tr;
            // garante enrolamento "para fora": compara a normal geométrica com a do vértice
            for (int t = 0; t < tri.length; t += 3) {
                int a = tri[t], b = tri[t + 1], c = tri[t + 2];
                double ux = px[b] - px[a], uy = py[b] - py[a], uz = pz[b] - pz[a];
                double vx = px[c] - px[a], vy = py[c] - py[a], vz = pz[c] - pz[a];
                double gx = uy * vz - uz * vy, gy = uz * vx - ux * vz, gz = ux * vy - uy * vx;
                double d = gx * (qx[a] + qx[b] + qx[c]) + gy * (qy[a] + qy[b] + qy[c]) + gz * (qz[a] + qz[b] + qz[c]);
                if (d < 0) { tri[t + 1] = c; tri[t + 2] = b; }
            }
        }
        static double spow(double x, double p) {
            double a = Math.abs(x);
            if (a < 1e-9) return 0;
            double r = Math.pow(a, p);
            return x < 0 ? -r : r;
        }
    }
    private static final Tmpl[] SPH = {new Tmpl(1f, 8, 5), new Tmpl(1f, 12, 7), new Tmpl(1f, 16, 10)};
    private static final Tmpl[] BOX = {new Tmpl(0.45f, 10, 6), new Tmpl(0.45f, 14, 8), new Tmpl(0.45f, 18, 10)};
    private static final Tmpl[] PIL = {new Tmpl(0.7f, 8, 5), new Tmpl(0.7f, 12, 7), new Tmpl(0.7f, 16, 10)};

    private int lod(float size) {
        float px = size * rs;
        return px < 2.2f ? 0 : (px < 6f ? 1 : 2);
    }

    private int newPrim(int col, boolean dbl) {
        if (np >= PMAX) return -1;
        pCol[np] = col; pMat[np] = (byte) mat; pOut[np] = outline; pDbl[np] = dbl; pBias[np] = bias + np * 0.00015f;
        return np++;
    }

    /** adiciona um vértice (posição local, normal local já normalizada) e devolve o índice */
    private int addV(float[] m, float x, float y, float z, float nx, float ny, float nz) {
        int i = nv++;
        float wxx = m[0] * x + m[1] * y + m[2] * z + m[9];
        float wyy = m[3] * x + m[4] * y + m[5] * z + m[10];
        float wzz = m[6] * x + m[7] * y + m[8] * z + m[11];
        vX[i] = ox + wxx * rs;
        vY[i] = oy + (wzz * SINP - wyy * COSP) * rs;
        vD[i] = wyy * SINP + wzz * COSP;
        float ax = m[0] * nx + m[1] * ny + m[2] * nz;
        float ay = m[3] * nx + m[4] * ny + m[5] * nz;
        float az = m[6] * nx + m[7] * ny + m[8] * nz;
        float l = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (l < 1e-8f) l = 1f;
        vNx[i] = ax / l; vNy[i] = ay / l; vNz[i] = az / l;
        return i;
    }

    private float det() {
        float[] m = st[top];
        return m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6]) + m[2] * (m[3] * m[7] - m[4] * m[6]);
    }

    private void addTris(int p, int base, int[] tri, boolean flip) {
        for (int t = 0; t < tri.length && nt < TMAX; t += 3) {
            tA[nt] = base + tri[t];
            tB[nt] = base + (flip ? tri[t + 2] : tri[t + 1]);
            tC[nt] = base + (flip ? tri[t + 1] : tri[t + 2]);
            tP[nt] = p;
            nt++;
        }
    }

    private void useTmpl(Tmpl tp, float cx, float cy, float cz, float hx, float hy, float hz, int col) {
        if (nv + tp.nVert >= VMAX || nt + tp.tri.length / 3 >= TMAX) return;
        int p = newPrim(col, false);
        if (p < 0) return;
        float[] m = st[top];
        int base = nv;
        float ix = 1f / hx, iy = 1f / hy, iz = 1f / hz;
        for (int k = 0; k < tp.nVert; k++) {
            float nx = tp.qx[k] * ix, ny = tp.qy[k] * iy, nz = tp.qz[k] * iz;
            float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1e-8f) { nx = 0; ny = 1; nz = 0; l = 1; }
            addV(m, cx + tp.px[k] * hx, cy + tp.py[k] * hy, cz + tp.pz[k] * hz, nx / l, ny / l, nz / l);
        }
        addTris(p, base, tp.tri, det() < 0f);
    }

    /** elipsoide liso (cabeça, músculo, bola de articulação) */
    public void ell(float cx, float cy, float cz, float rx, float ry, float rz, int col) {
        useTmpl(SPH[lod(Math.max(rx, Math.max(ry, rz)))], cx, cy, cz, rx, ry, rz, col);
    }
    /** caixa de cantos bem arredondados (armadura, bota) */
    public void rbox(float cx, float cy, float cz, float hx, float hy, float hz, int col) {
        useTmpl(BOX[lod(Math.max(hx, Math.max(hy, hz)))], cx, cy, cz, hx, hy, hz, col);
    }
    /** travesseiro: meio-termo entre caixa e elipsoide (couro, tecido volumoso) */
    public void pill(float cx, float cy, float cz, float hx, float hy, float hz, int col) {
        useTmpl(PIL[lod(Math.max(hx, Math.max(hy, hz)))], cx, cy, cz, hx, hy, hz, col);
    }

    // ───────── loft: tubo ao longo de Y local com anéis elípticos ─────────
    private static final int SIDES_MAX = 20;
    private final float[] eY = new float[40], eCx = new float[40], eCz = new float[40], eRx = new float[40], eRz = new float[40];
    private final float[] limbBs = new float[9];
    private final float[] ringN = new float[SIDES_MAX * 3];

    /**
     * Tubo vertical. Cada anel i: altura y[i], centro (cx[i], cz[i]), raios (rx[i], rz[i]).
     * capLo/capHi: comprimento da tampa arredondada embaixo/em cima (0 = sem tampa).
     * Normais vêm das diferenças entre anéis (liso).
     */
    public void loft(float[] y, float[] cx, float[] cz, float[] rx, float[] rz, int rings, float capLo, float capHi, int col) {
        float big = 0f;
        for (int i = 0; i < rings; i++) big = Math.max(big, Math.max(rx[i], rz[i]));
        int sides = big * rs < 2.5f ? 8 : (big * rs < 7f ? 12 : 16);
        int capSteps = (capLo > 0 ? 3 : 0) + (capHi > 0 ? 3 : 0);
        int total = rings + capSteps;
        if (total > 40) return;
        if (nv + total * sides + 2 >= VMAX || nt + total * sides * 2 + sides * 2 >= TMAX) return;
        int p = newPrim(col, false);
        if (p < 0) return;
        float[] m = st[top];
        int base = nv;
        int rowsAdded = 0;
        // anéis efetivos: tampa baixa (3 passos), anéis, tampa alta (3 passos)
        float[] ey = eY, ecx = eCx, ecz = eCz, erx = eRx, erz = eRz;
        int k = 0;
        if (capLo > 0) {
            for (int s = 0; s < 3; s++) {
                float ang = (float) (Math.PI / 2 * (3 - s) / 3.0);
                float f = (float) Math.cos(ang), dn = (float) Math.sin(ang);
                ey[k] = y[0] - capLo * dn; ecx[k] = cx[0]; ecz[k] = cz[0]; erx[k] = rx[0] * f; erz[k] = rz[0] * f; k++;
            }
        }
        for (int i = 0; i < rings; i++) { ey[k] = y[i]; ecx[k] = cx[i]; ecz[k] = cz[i]; erx[k] = rx[i]; erz[k] = rz[i]; k++; }
        if (capHi > 0) {
            for (int s = 1; s <= 3; s++) {
                float ang = (float) (Math.PI / 2 * s / 3.0);
                float f = (float) Math.cos(ang), up = (float) Math.sin(ang);
                int last = rings - 1;
                ey[k] = y[last] + capHi * up; ecx[k] = cx[last]; ecz[k] = cz[last]; erx[k] = rx[last] * f; erz[k] = rz[last] * f; k++;
            }
        }
        for (int i = 0; i < total; i++) {
            int i0 = Math.max(0, i - 1), i1 = Math.min(total - 1, i + 1);
            float dy = ey[i1] - ey[i0];
            float dcx = ecx[i1] - ecx[i0], dcz = ecz[i1] - ecz[i0];
            float drx = erx[i1] - erx[i0], drz = erz[i1] - erz[i0];
            if (Math.abs(dy) < 1e-5f) dy = 1e-5f;
            for (int j = 0; j < sides; j++) {
                double a = 2 * Math.PI * j / sides;
                float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                float px = ecx[i] + erx[i] * ca, pz = ecz[i] + erz[i] * sa, py = ey[i];
                // tangente angular T = (-rx sa, 0, rz ca); tangente vertical U = (dcx+drx ca, dy, dcz+drz sa)/..
                float tx = -erx[i] * sa, tz = erz[i] * ca;
                float ux = dcx + drx * ca, uy = dy, uz = dcz + drz * sa;
                // normal = U x T (aponta para fora quando y cresce para cima)
                float nx = uy * tz - uz * 0f, ny = uz * tx - ux * tz, nz = ux * 0f - uy * tx;
                float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (l < 1e-8f) { nx = ca; ny = 0f; nz = sa; l = 1f; }
                addV(m, px, py, pz, nx / l, ny / l, nz / l);
            }
        }
        boolean flip = det() < 0f;
        for (int i = 0; i < total - 1; i++) {
            for (int j = 0; j < sides; j++) {
                int j2 = (j + 1) % sides;
                int a = base + i * sides + j, b = base + i * sides + j2, c = base + (i + 1) * sides + j, d = base + (i + 1) * sides + j2;
                if (nt + 2 >= TMAX) break;
                // enrolamento escolhido para ficar "para fora" com a normal acima
                tA[nt] = a; tB[nt] = flip ? b : c; tC[nt] = flip ? c : b; tP[nt] = p; nt++;
                tA[nt] = b; tB[nt] = flip ? d : c; tC[nt] = flip ? c : d; tP[nt] = p; nt++;
            }
        }
    }

    /** membro cônico entre dois pontos locais (cápsula afunilada). rx/rz iguais = redondo. */
    private final float[] lY = new float[4], lCx = new float[4], lCz = new float[4], lRx = new float[4], lRz = new float[4];
    public void limb(float ax, float ay, float az, float bx, float by, float bz, float r0, float r1, int col) {
        limbE(ax, ay, az, bx, by, bz, r0, r0, r1, r1, col);
    }
    /** igual ao limb, mas com raios elípticos (largura x profundidade) em cada ponta */
    public void limbE(float ax, float ay, float az, float bx, float by, float bz, float rxa, float rza, float rxb, float rzb, int col) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-4f) { ell(ax, ay, az, rxa, rxa, rza, col); return; }
        // base ortonormal com Y = direção do membro
        float yx = dx / len, yy = dy / len, yz = dz / len;
        float rx0 = Math.abs(yy) < 0.9f ? 0f : 1f, ry0 = Math.abs(yy) < 0.9f ? 1f : 0f;
        // X = (ref x Y) normalizado
        float xx = ry0 * yz - 0f * yy, xy = 0f * yx - rx0 * yz, xz = rx0 * yy - ry0 * yx;
        float xl = (float) Math.sqrt(xx * xx + xy * xy + xz * xz);
        xx /= xl; xy /= xl; xz /= xl;
        float zx = xy * yz - xz * yy, zy = xz * yx - xx * yz, zz = xx * yy - xy * yx;
        push();
        float[] m = st[top];
        // m = m * [X Y Z] com translação em A
        float tx = m[0] * ax + m[1] * ay + m[2] * az, ty = m[3] * ax + m[4] * ay + m[5] * az, tz = m[6] * ax + m[7] * ay + m[8] * az;
        m[9] += tx; m[10] += ty; m[11] += tz;
        float[] bs = limbBs;
        bs[0] = xx; bs[1] = yx; bs[2] = zx; bs[3] = xy; bs[4] = yy; bs[5] = zy; bs[6] = xz; bs[7] = yz; bs[8] = zz;
        basis(bs);
        lY[0] = 0f; lY[1] = len * 0.5f; lY[2] = len;
        lCx[0] = lCx[1] = lCx[2] = 0f; lCz[0] = lCz[1] = lCz[2] = 0f;
        lRx[0] = rxa; lRx[2] = rxb; lRx[1] = (rxa + rxb) * 0.5f * 1.04f;
        lRz[0] = rza; lRz[2] = rzb; lRz[1] = (rza + rzb) * 0.5f * 1.04f;
        loft(lY, lCx, lCz, lRx, lRz, 3, Math.min(rxa, rza) * 0.9f, Math.min(rxb, rzb) * 0.9f, col);
        pop();
    }

    // ───────── tecido (grade nu x nv de pontos locais; dupla face) ─────────
    private final float[] gN = new float[3];
    /** P = nu*nv pontos (x,y,z) locais, linha a linha (v) com nu colunas. */
    public void cloth(float[] P, int nu, int nvv, int col) {
        if (nv + nu * nvv >= VMAX || nt + (nu - 1) * (nvv - 1) * 2 >= TMAX) return;
        int p = newPrim(col, true);
        if (p < 0) return;
        float[] m = st[top];
        int base = nv;
        for (int v = 0; v < nvv; v++) {
            for (int u = 0; u < nu; u++) {
                int u0 = Math.max(0, u - 1), u1 = Math.min(nu - 1, u + 1), v0 = Math.max(0, v - 1), v1 = Math.min(nvv - 1, v + 1);
                int a0 = (v * nu + u0) * 3, a1 = (v * nu + u1) * 3, b0 = (v0 * nu + u) * 3, b1 = (v1 * nu + u) * 3;
                float ux = P[a1] - P[a0], uy = P[a1 + 1] - P[a0 + 1], uz = P[a1 + 2] - P[a0 + 2];
                float vx = P[b1] - P[b0], vy = P[b1 + 1] - P[b0 + 1], vz = P[b1 + 2] - P[b0 + 2];
                float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (l < 1e-8f) { nx = 0; ny = 0; nz = 1; l = 1; }
                int k = (v * nu + u) * 3;
                addV(m, P[k], P[k + 1], P[k + 2], nx / l, ny / l, nz / l);
            }
        }
        for (int v = 0; v < nvv - 1; v++) {
            for (int u = 0; u < nu - 1; u++) {
                int a = base + v * nu + u, b = a + 1, c = a + nu, d = c + 1;
                tA[nt] = a; tB[nt] = b; tC[nt] = d; tP[nt] = p; nt++;
                tA[nt] = a; tB[nt] = d; tC[nt] = c; tP[nt] = p; nt++;
            }
        }
    }

    // ───────── rasterização ─────────
    private static int shade(int c, float f) {
        int a = c >>> 24;
        int r = (int) (((c >> 16) & 0xFF) * f), g = (int) (((c >> 8) & 0xFF) * f), b = (int) ((c & 0xFF) * f);
        if (r > 255) r = 255; if (g > 255) g = 255; if (b > 255) b = 255;
        if (r < 0) r = 0; if (g < 0) g = 0; if (b < 0) b = 0;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // luz principal (cima, esquerda, frente) e vetor para a câmera
    private static final float LXN, LYN, LZN;
    static {
        float lx = -0.50f, ly = 0.74f, lz = 0.46f;
        float l = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        LXN = lx / l; LYN = ly / l; LZN = lz / l;
    }

    private void ensure(int size) {
        if (zb.length < size) {
            int n = size + size / 4;
            zb = new float[n]; ib = new int[n]; pix = new int[n];
            nbx = new float[n]; nby = new float[n]; nbz = new float[n];
        }
    }

    /**
     * Rasteriza tudo. ssWant = fator de supersampling desejado; limitPixels = máximo de pixels no buffer.
     * Retorna false se não há nada para desenhar.
     */
    public boolean render(float ssWant, int limitPixels, float alpha) {
        if (nv == 0 || nt == 0) return false;
        float minX = 1e9f, minY = 1e9f, maxX = -1e9f, maxY = -1e9f;
        for (int i = 0; i < nv; i++) {
            if (vX[i] < minX) minX = vX[i]; if (vX[i] > maxX) maxX = vX[i];
            if (vY[i] < minY) minY = vY[i]; if (vY[i] > maxY) maxY = vY[i];
        }
        if (!(maxX > minX) || !(maxY > minY)) return false;
        bx0 = (float) Math.floor(minX - 3f);
        by0 = (float) Math.floor(minY - 3f);
        float wpx = maxX - bx0 + 3f, hpx = maxY - by0 + 3f;
        if (wpx > 2400f || hpx > 2400f) return false;
        float s = ssWant;
        if (wpx * hpx * s * s > limitPixels) s = (float) Math.sqrt(limitPixels / (wpx * hpx)) * 0.97f;
        if (s < 0.5f) return false;
        ss = s;
        outW = (int) (wpx * s) + 1;
        outH = (int) (hpx * s) + 1;
        int size = outW * outH;
        if (size > limitPixels) return false;
        ensure(size);
        java.util.Arrays.fill(zb, 0, size, -1e9f);
        java.util.Arrays.fill(ib, 0, size, 0);
        float vx = 0f, vy = SINP, vz = COSP; // para a câmera

        for (int t = 0; t < nt; t++) {
            int a = tA[t], b = tB[t], c = tC[t], p = tP[t];
            float x0 = (vX[a] - bx0) * s, y0 = (vY[a] - by0) * s;
            float x1 = (vX[b] - bx0) * s, y1 = (vY[b] - by0) * s;
            float x2 = (vX[c] - bx0) * s, y2 = (vY[c] - by0) * s;
            float area = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0);
            if (area > -1e-6f && area < 1e-6f) continue;
            // descarte de face de trás (só malhas fechadas): a área na tela tem sinal fixo para faces de frente
            if (!pDbl[p] && area > 0f) continue;
            float inv = 1f / area;
            int xa = (int) Math.floor(Math.min(x0, Math.min(x1, x2))), xb = (int) Math.ceil(Math.max(x0, Math.max(x1, x2)));
            int ya = (int) Math.floor(Math.min(y0, Math.min(y1, y2))), yb = (int) Math.ceil(Math.max(y0, Math.max(y1, y2)));
            if (xa < 0) xa = 0; if (ya < 0) ya = 0; if (xb >= outW) xb = outW - 1; if (yb >= outH) yb = outH - 1;
            float d0 = vD[a] + pBias[p], d1 = vD[b] + pBias[p], d2 = vD[c] + pBias[p];
            float ex0 = y1 - y2, ex1 = y2 - y0, ex2 = y0 - y1;   // d(w)/dx * area
            float ey0 = x2 - x1, ey1 = x0 - x2, ey2 = x1 - x0;
            int id = p + 1;
            for (int y = ya; y <= yb; y++) {
                float py = y + 0.5f;
                for (int x = xa; x <= xb; x++) {
                    float px = x + 0.5f;
                    float w0 = ((x1 - px) * (y2 - py) - (y1 - py) * (x2 - px)) * inv;
                    float w1 = ((x2 - px) * (y0 - py) - (y2 - py) * (x0 - px)) * inv;
                    float w2 = 1f - w0 - w1;
                    if (w0 < -0.002f || w1 < -0.002f || w2 < -0.002f) continue;
                    float z = w0 * d0 + w1 * d1 + w2 * d2;
                    int i = y * outW + x;
                    if (z <= zb[i]) continue;
                    zb[i] = z;
                    ib[i] = id;
                    nbx[i] = w0 * vNx[a] + w1 * vNx[b] + w2 * vNx[c];
                    nby[i] = w0 * vNy[a] + w1 * vNy[b] + w2 * vNy[c];
                    nbz[i] = w0 * vNz[a] + w1 * vNz[b] + w2 * vNz[c];
                }
            }
        }
        resolve(size, alpha);
        return true;
    }

    private static final int[] OFX = {-1, 1, 0, 0, -2, 2, 0, 0};
    private static final int[] OFY = {0, 0, -1, 1, 0, 0, -2, 2};

    private void resolve(int size, float alpha) {
        float vy = SINP, vz = COSP;
        // meio-vetor para o brilho
        float hx = LXN, hy = LYN + vy, hz = LZN + vz;
        float hl = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
        hx /= hl; hy /= hl; hz /= hl;
        int al = (int) (Math.max(0f, Math.min(1f, alpha)) * 255f);
        int[] cb = pix;
        for (int i = 0; i < size; i++) {
            int id = ib[i];
            if (id == 0) { cb[i] = 0; continue; }
            int p = id - 1;
            int base = pCol[p];
            int mt = pMat[p];
            float nx = nbx[i], ny = nby[i], nz = nbz[i];
            float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1e-6f) { nx = 0; ny = 1; nz = 0; } else { nx /= l; ny /= l; nz /= l; }
            if (pDbl[p] && (nx * 0f + ny * vy + nz * vz) < 0f) { nx = -nx; ny = -ny; nz = -nz; }
            if (mt == M_GLOW) { cb[i] = (al << 24) | (base & 0xFFFFFF); continue; }
            float dl = nx * LXN + ny * LYN + nz * LZN;
            float w = WRAP[mt];
            float wrap = (dl + w) / (1f + w);
            if (wrap < 0f) wrap = 0f;
            float hemi = 0.5f + 0.5f * ny;
            float sh = 0.36f + 0.18f * hemi + 0.66f * wrap;
            // oclusão falsa: base dos volumes mais escura
            float rim = 1f - (ny * vy + nz * vz);
            if (rim < 0f) rim = 0f;
            float rim3 = rim * rim * rim;
            int r = (base >> 16) & 0xFF, g = (base >> 8) & 0xFF, b = base & 0xFF;
            // luz quente no lado iluminado, fria na sombra
            float warm = wrap;
            float fr = sh * (0.90f + 0.14f * warm), fg = sh * (0.95f + 0.05f * warm), fb = sh * (1.08f - 0.14f * warm);
            float rr = r * fr, gg = g * fg, bb = b * fb;
            float sp = SPEC[mt];
            if (sp > 0f) {
                float nh = nx * hx + ny * hy + nz * hz;
                if (nh > 0f) {
                    float sv = sp * (float) Math.pow(nh, SHIN[mt]) * (0.4f + 0.6f * wrap) * 255f;
                    rr += sv; gg += sv; bb += sv * 0.95f;
                }
            }
            // luz de contorno (rim) suave, mais forte em cima
            float rimK = 14f * rim3 * (0.4f + 0.6f * hemi);
            rr += rimK * 0.8f; gg += rimK * 0.9f; bb += rimK;
            int ir = (int) rr, ig = (int) gg, ib2 = (int) bb;
            if (ir > 255) ir = 255; if (ig > 255) ig = 255; if (ib2 > 255) ib2 = 255;
            if (ir < 0) ir = 0; if (ig < 0) ig = 0; if (ib2 < 0) ib2 = 0;
            cb[i] = (al << 24) | (ir << 16) | (ig << 8) | ib2;
        }
        // contorno: forte na silhueta e nos saltos de profundidade, suave entre peças
        final float EDGE_Z = 0.9f;
        int[] tmp = null;
        for (int y = 0; y < outH; y++) {
            int row = y * outW;
            for (int x = 0; x < outW; x++) {
                int i = row + x;
                int id = ib[i];
                if (id == 0) continue;
                if (!pOut[id - 1]) continue;
                float z = zb[i];
                boolean strong = false, soft = false;
                for (int k = 0; k < 8; k++) {
                    int nx2 = x + OFX[k], ny2 = y + OFY[k];
                    int nid = 0; float nz2 = 0f;
                    if (nx2 >= 0 && nx2 < outW && ny2 >= 0 && ny2 < outH) {
                        int j = ny2 * outW + nx2;
                        nid = ib[j]; nz2 = zb[j];
                    }
                    if (nid == 0) { strong = true; break; }
                    if (nid != id) {
                        if (nz2 < z - EDGE_Z) { strong = true; break; }
                        else if (k < 4 && nid < id && nz2 <= z + EDGE_Z) soft = true;
                    }
                }
                if (strong) cb[i] = shade(cb[i], 0.40f);
                else if (soft) cb[i] = shade(cb[i], 0.72f);
            }
        }
    }
}
