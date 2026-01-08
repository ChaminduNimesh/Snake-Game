import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.prefs.Preferences;
import javax.sound.sampled.*;

public class SnakeReal90 extends JPanel implements ActionListener, KeyListener {

    // ===== Fullscreen =====
    private final JFrame frame;
    private GraphicsDevice device;
    private boolean fullscreen = true;

    // ===== Game =====
    private enum State { MENU, RUNNING, PAUSED, GAME_OVER }
    private enum Dir { UP, DOWN, LEFT, RIGHT }
    private enum Food { NORMAL, GOLD }

    private State state = State.MENU;
    private Dir dir = Dir.RIGHT;
    private Dir nextDir = Dir.RIGHT;

    // Grid for logic (NOT drawn)
    private static final int COLS = 40;
    private static final int ROWS = 24;
    private static final int MAX  = COLS * ROWS;

    // current grid positions
    private final int[] sx = new int[MAX];
    private final int[] sy = new int[MAX];

    // previous grid positions (for smooth animation)
    private final int[] px = new int[MAX];
    private final int[] py = new int[MAX];

    private int len = 7;

    private int foodX, foodY;
    private Food foodType = Food.NORMAL;

    private int score = 0;
    private int best  = 0;

    // Step timing
    private int baseStepMs = 95;
    private int stepMs = baseStepMs;

    // Rendering scale
    private int tile = 24;
    private int offX = 0, offY = 0;

    // Animation timing
    private final javax.swing.Timer frameTimer = new javax.swing.Timer(16, this);
    private long lastNanos = System.nanoTime();
    private double accMs = 0.0;
    private double alpha = 0.0;

    // Background stars
    private static class Star { float x, y, s, r; }
    private final Star[] stars = new Star[160];

    private final Random rng = new Random();
    private final Preferences prefs = Preferences.userNodeForPackage(SnakeReal90.class);

    // Sounds (no external wav needed)
    private final SoundFX sfx = new SoundFX();

    public SnakeReal90(JFrame frame) {
        this.frame = frame;
        setFocusable(true);
        addKeyListener(this);

        best = prefs.getInt("snake_real90_best", 0);
        initStars();

        frameTimer.start();
        lastNanos = System.nanoTime();
    }

    private void initStars() {
        for (int i = 0; i < stars.length; i++) {
            Star st = new Star();
            st.x = rng.nextFloat();
            st.y = rng.nextFloat();
            st.s = 0.06f + rng.nextFloat() * 0.28f;
            st.r = 1.0f + rng.nextFloat() * 2.8f;
            stars[i] = st;
        }
    }

    private void updateStars(float dt) {
        for (Star st : stars) {
            st.y += st.s * dt;
            if (st.y > 1.1f) {
                st.y = -0.1f;
                st.x = rng.nextFloat();
                st.s = 0.06f + rng.nextFloat() * 0.28f;
                st.r = 1.0f + rng.nextFloat() * 2.8f;
            }
        }
    }

    private void recomputeScale() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        tile = Math.max(14, Math.min(w / COLS, h / ROWS));
        offX = (w - COLS * tile) / 2;
        offY = (h - ROWS * tile) / 2;
    }

    // ===== Game flow =====
    private void startGame() {
        score = 0;
        len = 7;
        dir = Dir.RIGHT;
        nextDir = Dir.RIGHT;

        int startX = COLS / 2;
        int startY = ROWS / 2;

        for (int i = 0; i < len; i++) {
            sx[i] = startX - i;
            sy[i] = startY;
            px[i] = sx[i];
            py[i] = sy[i];
        }

        stepMs = baseStepMs;
        accMs = 0.0;
        alpha = 0.0;
        lastNanos = System.nanoTime();

        spawnFood();
        state = State.RUNNING;
        repaint();
    }

    private void spawnFood() {
        foodType = (rng.nextInt(100) < 16) ? Food.GOLD : Food.NORMAL;

        while (true) {
            foodX = rng.nextInt(COLS);
            foodY = rng.nextInt(ROWS);

            boolean onSnake = false;
            for (int i = 0; i < len; i++) {
                if (sx[i] == foodX && sy[i] == foodY) { onSnake = true; break; }
            }
            if (!onSnake) break;
        }
    }

    private void gameOver() {
        state = State.GAME_OVER;
        sfx.playDie();

        if (score > best) {
            best = score;
            prefs.putInt("snake_real90_best", best);
        }
        repaint();
    }

    // ===== Logic step (classic 90° movement) =====
    private void doStep() {
        // snapshot current -> prev for animation
        for (int i = 0; i < len; i++) {
            px[i] = sx[i];
            py[i] = sy[i];
        }

        dir = nextDir;

        // shift body
        for (int i = len - 1; i > 0; i--) {
            sx[i] = sx[i - 1];
            sy[i] = sy[i - 1];
        }

        // move head
        switch (dir) {
            case UP -> sy[0]--;
            case DOWN -> sy[0]++;
            case LEFT -> sx[0]--;
            case RIGHT -> sx[0]++;
        }

        // wrap edges (arcade)
        if (sx[0] < 0) sx[0] = COLS - 1;
        if (sx[0] >= COLS) sx[0] = 0;
        if (sy[0] < 0) sy[0] = ROWS - 1;
        if (sy[0] >= ROWS) sy[0] = 0;

        // self collision
        for (int i = 1; i < len; i++) {
            if (sx[0] == sx[i] && sy[0] == sy[i]) {
                gameOver();
                return;
            }
        }

        // eat
        if (sx[0] == foodX && sy[0] == foodY) {
            int oldLen = len;
            len = Math.min(MAX, len + 1);

            // keep new tail stable
            px[len - 1] = px[oldLen - 1];
            py[len - 1] = py[oldLen - 1];
            sx[len - 1] = sx[oldLen - 1];
            sy[len - 1] = sy[oldLen - 1];

            score += (foodType == Food.GOLD) ? 30 : 10;

            // speed up gradually
            stepMs = Math.max(45, baseStepMs - (score / 80) * 3);

            sfx.playEat();
            spawnFood();

            if (score > best) {
                best = score;
                prefs.putInt("snake_real90_best", best);
            }
        }
    }

    // ===== Loop =====
    @Override
    public void actionPerformed(ActionEvent e) {
        long now = System.nanoTime();
        float dt = (now - lastNanos) / 1_000_000_000f;
        lastNanos = now;

        updateStars(dt);

        if (state == State.RUNNING) {
            accMs += dt * 1000.0;

            while (accMs >= stepMs && state == State.RUNNING) {
                accMs -= stepMs;
                doStep();
            }

            alpha = clamp(accMs / stepMs, 0.0, 1.0);
        } else {
            alpha = 1.0;
        }

        repaint();
    }

    // ===== Rendering =====
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        recomputeScale();

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // background
        GradientPaint bg = new GradientPaint(0, 0, new Color(6, 10, 22), getWidth(), getHeight(), new Color(0, 0, 0));
        g2.setPaint(bg);
        g2.fillRect(0, 0, getWidth(), getHeight());

        // stars
        for (Star st : stars) {
            int x = (int) (st.x * getWidth());
            int y = (int) (st.y * getHeight());
            int r = (int) st.r;
            g2.setColor(new Color(255, 255, 255, 70));
            g2.fillOval(x, y, r, r);
        }

        // board glow frame
        g2.setColor(new Color(120, 160, 255, 22));
        g2.fillRoundRect(offX - 18, offY - 18, COLS * tile + 36, ROWS * tile + 36, 34, 34);

        // food
        drawFood(g2);

        // snake
        drawSnakeRounded90_NoCrossScreenBug(g2);

        // HUD + overlays
        drawHUD(g2);
        if (state == State.MENU) drawMenu(g2);
        if (state == State.PAUSED) drawPause(g2);
        if (state == State.GAME_OVER) drawGameOver(g2);

        g2.dispose();
    }

    private void drawFood(Graphics2D g2) {
        double t = System.nanoTime() / 1_000_000_000.0;
        float pulse = (float)(0.85 + 0.15 * Math.sin(t * 6.0));

        int px = offX + foodX * tile;
        int py = offY + foodY * tile;

        int size = (int)(tile * 0.70f * pulse);
        int cx = px + (tile - size) / 2;
        int cy = py + (tile - size) / 2;

        Color core = (foodType == Food.GOLD) ? new Color(255, 200, 70) : new Color(255, 80, 140);

        for (int i = 3; i >= 1; i--) {
            int gs = size + i * (tile / 2);
            int gx = px + (tile - gs) / 2;
            int gy = py + (tile - gs) / 2;
            g2.setColor(new Color(core.getRed(), core.getGreen(), core.getBlue(), 16 * i));
            g2.fillOval(gx, gy, gs, gs);
        }

        g2.setColor(new Color(core.getRed(), core.getGreen(), core.getBlue(), 235));
        g2.fillOval(cx, cy, size, size);

        g2.setColor(new Color(255, 255, 255, 150));
        g2.fillOval(cx + size/5, cy + size/5, size/3, size/3);
    }

    // ✅ FIX: split the path whenever wrapping would draw a huge line across the screen
    private void drawSnakeRounded90_NoCrossScreenBug(Graphics2D g2) {
        if (len < 2) return;

        // convert snake cells -> pixel center points (still wrapped 0..COLS-1 etc.)
        ArrayList<Point2D.Double> pts = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            double gx = interpWrap(px[i], sx[i], COLS, alpha);
            double gy = interpWrap(py[i], sy[i], ROWS, alpha);
            double x = offX + (gx + 0.5) * tile;
            double y = offY + (gy + 0.5) * tile;
            pts.add(new Point2D.Double(x, y));
        }

        // split into segments where a wrap jump happens (prevents “screen-bridge” bug)
        double jump = tile * 1.6; // threshold: larger than a normal neighbor distance
        ArrayList<ArrayList<Point2D.Double>> segments = new ArrayList<>();
        ArrayList<Point2D.Double> cur = new ArrayList<>();
        cur.add(pts.get(0));

        for (int i = 1; i < pts.size(); i++) {
            Point2D.Double a = pts.get(i - 1);
            Point2D.Double b = pts.get(i);
            double dx = Math.abs(b.x - a.x);
            double dy = Math.abs(b.y - a.y);

            // If it jumps far in x or y, it's a wrap -> start a new segment
            if (dx > jump || dy > jump) {
                segments.add(cur);
                cur = new ArrayList<>();
                cur.add(b);
            } else {
                cur.add(b);
            }
        }
        segments.add(cur);

        float thickness = Math.max(10f, tile * 0.55f);
        double cornerR = Math.max(6, tile * 0.32);

        // draw each segment with rounded 90° corners
        for (ArrayList<Point2D.Double> seg : segments) {
            if (seg.size() < 2) continue;
            Path2D path = buildRoundedCornerPath(seg, cornerR);

            // glow
            g2.setStroke(new BasicStroke(thickness * 1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(0, 255, 210, 35));
            g2.draw(path);

            // main
            g2.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(0, 230, 175, 230));
            g2.draw(path);

            // highlight
            g2.setStroke(new BasicStroke(Math.max(2f, thickness * 0.35f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(210, 255, 245, 140));
            g2.draw(path);
        }

        // head always drawn (nice)
        Point2D.Double head = pts.get(0);
        drawHead(g2, head.x, head.y, thickness);
    }

    private void drawHead(Graphics2D g2, double hx, double hy, float thickness) {
        double r = thickness * 0.75;

        g2.setColor(new Color(0, 255, 210, 50));
        g2.fill(new Ellipse2D.Double(hx - r - 10, hy - r - 10, (r + 10) * 2, (r + 10) * 2));

        g2.setColor(new Color(0, 245, 195, 240));
        g2.fill(new Ellipse2D.Double(hx - r, hy - r, r * 2, r * 2));

        // eyes direction based on current dir (still 90°)
        double fx = 0, fy = 0;
        if (dir == Dir.UP) fy = -1;
        if (dir == Dir.DOWN) fy = 1;
        if (dir == Dir.LEFT) fx = -1;
        if (dir == Dir.RIGHT) fx = 1;

        double sx = -fy, sy = fx;

        double eyeF = r * 0.35;
        double eyeS = r * 0.28;
        double eyeR = Math.max(2.5, r * 0.18);

        double e1x = hx + fx * eyeF + sx * eyeS;
        double e1y = hy + fy * eyeF + sy * eyeS;
        double e2x = hx + fx * eyeF - sx * eyeS;
        double e2y = hy + fy * eyeF - sy * eyeS;

        g2.setColor(new Color(255, 255, 255, 220));
        g2.fill(new Ellipse2D.Double(e1x - eyeR, e1y - eyeR, eyeR * 2, eyeR * 2));
        g2.fill(new Ellipse2D.Double(e2x - eyeR, e2y - eyeR, eyeR * 2, eyeR * 2));

        double pupilR = eyeR * 0.55;
        g2.setColor(new Color(10, 10, 10, 180));
        g2.fill(new Ellipse2D.Double(e1x - pupilR + fx * 1.8, e1y - pupilR + fy * 1.8, pupilR * 2, pupilR * 2));
        g2.fill(new Ellipse2D.Double(e2x - pupilR + fx * 1.8, e2y - pupilR + fy * 1.8, pupilR * 2, pupilR * 2));
    }

    // Rounded-corner polyline path
    private static Path2D buildRoundedCornerPath(java.util.List<Point2D.Double> pts, double cornerR) {
        int n = pts.size();
        Path2D p = new Path2D.Double();
        p.moveTo(pts.get(0).x, pts.get(0).y);

        if (n == 2) {
            p.lineTo(pts.get(1).x, pts.get(1).y);
            return p;
        }

        for (int i = 1; i < n - 1; i++) {
            Point2D.Double P0 = pts.get(i - 1);
            Point2D.Double P1 = pts.get(i);
            Point2D.Double P2 = pts.get(i + 1);

            double vx1 = P1.x - P0.x, vy1 = P1.y - P0.y;
            double vx2 = P2.x - P1.x, vy2 = P2.y - P1.y;

            double len1 = Math.hypot(vx1, vy1);
            double len2 = Math.hypot(vx2, vy2);

            if (len1 < 1e-6 || len2 < 1e-6) {
                p.lineTo(P1.x, P1.y);
                continue;
            }

            double ux1 = vx1 / len1, uy1 = vy1 / len1;
            double ux2 = vx2 / len2, uy2 = vy2 / len2;

            double dot = ux1 * ux2 + uy1 * uy2;
            if (Math.abs(dot - 1.0) < 1e-3) {
                p.lineTo(P1.x, P1.y);
                continue;
            }

            double r = Math.min(cornerR, Math.min(len1, len2) * 0.5);

            double ax = P1.x - ux1 * r;
            double ay = P1.y - uy1 * r;
            double bx = P1.x + ux2 * r;
            double by = P1.y + uy2 * r;

            p.lineTo(ax, ay);
            p.quadTo(P1.x, P1.y, bx, by);
        }

        p.lineTo(pts.get(n - 1).x, pts.get(n - 1).y);
        return p;
    }

    private void drawHUD(Graphics2D g2) {
        g2.setFont(new Font("Consolas", Font.BOLD, 20));
        g2.setColor(new Color(255, 255, 255, 230));
        g2.drawString("Score: " + score, 18, 34);

        String right = "Best: " + best + "   F11 Fullscreen   P Pause   ESC Menu";
        int sw = g2.getFontMetrics().stringWidth(right);
        g2.drawString(right, getWidth() - sw - 18, 34);
    }

    private void panelOverlay(Graphics2D g2, String title) {
        int w = Math.min(860, getWidth() - 140);
        int h = Math.min(420, getHeight() - 160);
        int x = (getWidth() - w) / 2;
        int y = (getHeight() - h) / 2;

        g2.setColor(new Color(0, 0, 0, 175));
        g2.fillRoundRect(x, y, w, h, 30, 30);
        g2.setColor(new Color(255, 255, 255, 45));
        g2.drawRoundRect(x, y, w, h, 30, 30);

        g2.setFont(new Font("Arial", Font.BOLD, 44));
        g2.setColor(new Color(255, 255, 255, 240));
        int tw = g2.getFontMetrics().stringWidth(title);
        g2.drawString(title, (getWidth() - tw) / 2, y + 78);
    }

    private void drawMenu(Graphics2D g2) {
        panelOverlay(g2, "REAL SNAKE (90° CURVES)");
        g2.setFont(new Font("Arial", Font.PLAIN, 20));
        g2.setColor(new Color(255, 255, 255, 220));

        int y = getHeight()/2 - 30;
        drawCenter(g2, "ENTER  →  Start", y); y += 34;
        drawCenter(g2, "Arrow Keys  →  Classic 90° movement", y); y += 28;
        drawCenter(g2, "P Pause   R Restart   F11 Fullscreen   ESC Menu", y);
    }

    private void drawPause(Graphics2D g2) {
        panelOverlay(g2, "PAUSED");
        g2.setFont(new Font("Arial", Font.PLAIN, 20));
        g2.setColor(new Color(255, 255, 255, 220));
        drawCenter(g2, "Press P to Resume", getHeight()/2 + 10);
        drawCenter(g2, "Press R to Restart", getHeight()/2 + 44);
    }

    private void drawGameOver(Graphics2D g2) {
        panelOverlay(g2, "GAME OVER");
        g2.setFont(new Font("Arial", Font.PLAIN, 20));
        g2.setColor(new Color(255, 255, 255, 220));
        drawCenter(g2, "Final Score: " + score, getHeight()/2 - 6);
        drawCenter(g2, "Best Score: " + best, getHeight()/2 + 28);
        drawCenter(g2, "Press R to Restart  |  ESC Menu", getHeight()/2 + 62);
    }

    private void drawCenter(Graphics2D g2, String s, int y) {
        int sw = g2.getFontMetrics().stringWidth(s);
        g2.drawString(s, (getWidth() - sw) / 2, y);
    }

    // ===== Input =====
    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();

        if (k == KeyEvent.VK_F11) { toggleFullscreen(); return; }

        if (k == KeyEvent.VK_ESCAPE) {
            if (fullscreen) exitFullscreen();
            state = State.MENU;
            repaint();
            return;
        }

        if (state == State.MENU) {
            if (k == KeyEvent.VK_ENTER) startGame();
            return;
        }

        if (k == KeyEvent.VK_P) {
            if (state == State.RUNNING) state = State.PAUSED;
            else if (state == State.PAUSED) state = State.RUNNING;
            return;
        }

        if (k == KeyEvent.VK_R) { startGame(); return; }

        if (state != State.RUNNING) return;

        // prevent instant reverse
        switch (k) {
            case KeyEvent.VK_UP    -> { if (dir != Dir.DOWN)  nextDir = Dir.UP; }
            case KeyEvent.VK_DOWN  -> { if (dir != Dir.UP)    nextDir = Dir.DOWN; }
            case KeyEvent.VK_LEFT  -> { if (dir != Dir.RIGHT) nextDir = Dir.LEFT; }
            case KeyEvent.VK_RIGHT -> { if (dir != Dir.LEFT)  nextDir = Dir.RIGHT; }
        }
    }
    @Override public void keyTyped(KeyEvent e) {}
    @Override public void keyReleased(KeyEvent e) {}

    // ===== Fullscreen =====
    private void toggleFullscreen() {
        if (fullscreen) exitFullscreen();
        else enterFullscreen();
    }

    private void enterFullscreen() {
        if (device == null) device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        fullscreen = true;

        frame.dispose();
        frame.setUndecorated(true);

        if (device.isFullScreenSupported()) device.setFullScreenWindow(frame);
        else frame.setExtendedState(JFrame.MAXIMIZED_BOTH);

        frame.setVisible(true);
        requestFocusInWindow();
    }

    private void exitFullscreen() {
        if (device == null) device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        fullscreen = false;

        if (device.getFullScreenWindow() == frame) device.setFullScreenWindow(null);

        frame.dispose();
        frame.setUndecorated(false);
        frame.setSize(1280, 720);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        requestFocusInWindow();
    }

    // ===== Helpers =====
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    // Interpolate grid positions smoothly across wrap boundaries (for animation)
    private static double interpWrap(int a, int b, int size, double t) {
        int da = b - a;
        if (Math.abs(da) > size / 2) {
            if (da > 0) a += size; else b += size;
        }
        double v = a + (b - a) * t;
        v = v % size;
        if (v < 0) v += size;
        return v;
    }

    // ===== Sound FX (generated tones) =====
    private static class SoundFX {
        private final Clip eatClip;
        private final Clip dieClip;

        SoundFX() {
            eatClip = makeToneClip(new double[]{880, 1040}, new int[]{70, 70}, 0.35);
            dieClip = makeToneClip(new double[]{520, 420, 320, 240}, new int[]{90, 90, 110, 140}, 0.45);
        }

        void playEat() { play(eatClip); }
        void playDie() { play(dieClip); }

        private static void play(Clip c) {
            if (c == null) return;
            if (c.isRunning()) c.stop();
            c.setFramePosition(0);
            c.start();
        }

        private static Clip makeToneClip(double[] freqs, int[] ms, double volume) {
            try {
                float sampleRate = 44100f;
                byte[] data = synth(freqs, ms, sampleRate, volume);

                AudioFormat fmt = new AudioFormat(sampleRate, 16, 1, true, false);
                AudioInputStream ais = new AudioInputStream(
                        new java.io.ByteArrayInputStream(data),
                        fmt,
                        data.length / 2
                );

                Clip clip = AudioSystem.getClip();
                clip.open(ais);
                return clip;
            } catch (Exception ex) {
                // If audio device not available, silently ignore
                return null;
            }
        }

        private static byte[] synth(double[] freqs, int[] ms, float sr, double volume) {
            int totalSamples = 0;
            for (int d : ms) totalSamples += (int)(sr * (d / 1000.0));

            short[] pcm = new short[totalSamples];
            int idx = 0;

            for (int i = 0; i < freqs.length; i++) {
                double f = freqs[i];
                int samples = (int)(sr * (ms[i] / 1000.0));
                int fade = Math.max(1, (int)(samples * 0.12));

                for (int n = 0; n < samples && idx < pcm.length; n++, idx++) {
                    double t = n / sr;
                    double s = Math.sin(2.0 * Math.PI * f * t);

                    // smooth envelope (fade in/out)
                    double env = 1.0;
                    if (n < fade) env = n / (double)fade;
                    else if (n > samples - fade) env = (samples - n) / (double)fade;

                    double val = s * env * volume;
                    pcm[idx] = (short)(val * 32767);
                }
            }

            byte[] out = new byte[pcm.length * 2];
            int bi = 0;
            for (short v : pcm) {
                out[bi++] = (byte)(v & 0xFF);
                out[bi++] = (byte)((v >> 8) & 0xFF);
            }
            return out;
        }
    }

    // ===== Main =====
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Snake Real 90");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            SnakeReal90 game = new SnakeReal90(f);
            f.add(game);

            f.setSize(1280, 720);
            f.setLocationRelativeTo(null);
            f.setVisible(true);

            game.enterFullscreen();
            game.requestFocusInWindow();
        });
    }
}
