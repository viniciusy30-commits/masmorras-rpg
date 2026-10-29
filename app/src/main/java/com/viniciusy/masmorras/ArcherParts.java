package com.viniciusy.masmorras;

/**
 * Volumes orgânicos do Arqueiro (coxa, panturrilha, pé, braço, antebraço, peitoral, pescoço).
 * Substituem os blocos grandes; os detalhes (fivelas, cadarços, placas) continuam como peças arredondadas.
 * Todos desenham no referencial atual do Gfx3D (quem chama copia a matriz antes).
 */
public final class ArcherParts {
    private ArcherParts() {}

    private static final float[] Y = new float[8], CX = new float[8], CZ = new float[8], RX = new float[8], RZ = new float[8];

    private static void ring(int i, float y, float cx, float cz, float rx, float rz) {
        Y[i] = y; CX[i] = cx; CZ[i] = cz; RX[i] = rx; RZ[i] = rz;
    }

    /** coxa: do quadril (y=+0.2) ao joelho (y=-5.6), mais larga em cima */
    public static void thigh(Gfx3D g, int col) {
        ring(0, -5.7f, 0f, 0.05f, 1.72f, 1.85f);
        ring(1, -4.2f, 0f, 0.12f, 2.05f, 2.15f);
        ring(2, -2.2f, 0f, 0.15f, 2.30f, 2.35f);
        ring(3, -0.2f, 0f, 0.05f, 2.30f, 2.30f);
        g.loft(Y, CX, CZ, RX, RZ, 4, 1.4f, 1.0f, col);
        g.ell(0f, -5.6f, 0.15f, 1.85f, 1.6f, 1.95f, col); // joelho
    }

    /** cano da bota: do joelho (y=-0.3) ao tornozelo (y=-5.8), panturrilha atrás */
    public static void calf(Gfx3D g, int col) {
        ring(0, -5.9f, 0f, 0.15f, 1.55f, 1.65f);
        ring(1, -4.2f, 0f, 0.05f, 1.85f, 1.95f);
        ring(2, -2.2f, 0f, -0.25f, 2.10f, 2.15f);
        ring(3, -0.2f, 0f, 0.0f, 1.95f, 2.05f);
        g.loft(Y, CX, CZ, RX, RZ, 4, 0.8f, 0.6f, col);
    }

    /** pé da bota: do calcanhar à ponta, afunilando para a biqueira */
    public static void foot(Gfx3D g, int col) {
        g.limbE(0f, 0.15f, -1.7f, 0f, 0.15f, 3.6f, 1.95f, 1.45f, 1.55f, 1.05f, col);
        g.ell(0f, 0.2f, 4.0f, 1.45f, 1.0f, 1.25f, col); // biqueira
    }

    /** braço (parte de cima): ombro (y=0.2) ao cotovelo (y=-5.6), com bíceps */
    public static void upperArm(Gfx3D g, int col) {
        ring(0, -5.7f, 0f, 0f, 1.55f, 1.6f);
        ring(1, -4.0f, 0f, 0.15f, 1.85f, 1.95f);
        ring(2, -1.8f, 0f, 0.2f, 2.0f, 2.1f);
        ring(3, 0.1f, 0f, 0f, 2.1f, 2.1f);
        g.loft(Y, CX, CZ, RX, RZ, 4, 1.1f, 1.2f, col);
        g.ell(0f, -5.7f, 0f, 1.65f, 1.5f, 1.65f, col); // cotovelo
    }

    /** antebraço (parte de tecido): do cotovelo (y=0) ao início da manopla (y=-4.8) */
    public static void foreArm(Gfx3D g, int col) {
        ring(0, -4.9f, 0f, 0f, 1.75f, 1.8f);
        ring(1, -2.8f, 0f, 0.1f, 2.05f, 2.1f);
        ring(2, -0.6f, 0f, 0.05f, 2.05f, 2.1f);
        g.loft(Y, CX, CZ, RX, RZ, 3, 0.6f, 0.5f, col);
    }

    /** tronco: cintura (y=5.0) até o topo dos ombros (y=14.6), peito largo e levemente para a frente */
    public static void chest(Gfx3D g, int col) {
        ring(0, 4.7f, 0f, 0f, 4.3f, 3.0f);
        ring(1, 7.0f, 0f, 0.1f, 4.9f, 3.3f);
        ring(2, 9.8f, 0f, 0.25f, 5.55f, 3.5f);
        ring(3, 12.6f, 0f, 0.15f, 5.75f, 3.45f);
        ring(4, 14.4f, 0f, -0.05f, 4.6f, 2.9f);
        g.loft(Y, CX, CZ, RX, RZ, 5, 0.9f, 0.9f, col);
    }

    /** pescoço */
    public static void neck(Gfx3D g, int col) {
        ring(0, -1.2f, 0f, 0f, 1.95f, 2.0f);
        ring(1, 0.6f, 0f, 0.05f, 1.75f, 1.8f);
        ring(2, 2.0f, 0f, 0.15f, 1.85f, 1.9f);
        g.loft(Y, CX, CZ, RX, RZ, 3, 0.4f, 0.6f, col);
    }
}
