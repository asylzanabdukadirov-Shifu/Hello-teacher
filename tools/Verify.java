import java.util.*;

/** Extra independent checks. Run after compiling: java -cp out tools/Verify.java */
public class Verify {
    private static int checks;

    public static void main(String[] args) {
        List<String> words = new ArrayList<>();
        words.add("");
        for (int length = 1; length <= 4; length++) {
            for (int mask = 0; mask < (1 << length); mask++) {
                StringBuilder s = new StringBuilder();
                for (int i = 0; i < length; i++) s.append((mask & (1 << i)) == 0 ? 'A' : 'C');
                words.add(s.toString());
            }
        }
        for (String a : words) {
            Map<String, Integer> distances = editGraphDistances(a, 4);
            for (String b : words) {
                equal(distances.get(b), Strains.editDistance(a, b), "edit distance");
                equal(bruteLcs(a, b), Strains.lcsLength(a, b), "LCS");
            }
        }

        Random random = new Random(250788);
        for (int trial = 0; trial < 1000; trial++) {
            String a = dna(random, random.nextInt(31));
            String b = dna(random, random.nextInt(21));
            String p = dna(random, random.nextInt(9));
            int[] pi = Marker.prefixFunction(p);
            for (int end = 0; end < p.length(); end++) {
                int border = 0;
                for (int len = 1; len <= end; len++) {
                    if (same(p, 0, p, end + 1 - len, len)) border = len;
                }
                equal(border, pi[end], "prefix function");
            }
            List<Integer> expected = new ArrayList<>();
            for (int i = 0; i + p.length() <= a.length(); i++) {
                if (same(a, i, p, 0, p.length())) expected.add(i);
            }
            int[] got = Marker.findAll(a, p);
            equal(expected.size(), got.length, "KMP count");
            for (int i = 0; i < got.length; i++) equal(expected.get(i), got[i], "KMP position");
            for (int k = 0; k <= 13; k++) {
                equal(bruteShared(a, b, k), Marker.sharedWords(a, b, k), "shared words");
            }
        }
        equal(3, Marker.findAll("AAAA", "AA").length, "overlapping hits");
        equal(4, Marker.sharedWords("AAAAAA", "AAAA", 3), "count A positions");
        String x = "CAGAGGGCGCAC", y = "CAACGTGGGAGT";
        equal(hash(x), hash(y), "collision fixture");
        equal(0, Marker.sharedWords(x, y, 12), "reject a real hash collision");
        equal(1, Marker.sharedWords(x, y + x, 12), "check later candidates in a hash bucket");
        boolean rejected = false;
        try { Marker.sharedWords("A", "A", -1); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "negative window length");

        for (int trial = 0; trial < 400; trial++) {
            int n = 1 + random.nextInt(10);
            int[] a = new int[n - 1], b = new int[n - 1], costs = new int[n];
            for (int i = 1; i < n; i++) {
                a[i - 1] = i;
                b[i - 1] = random.nextInt(i);
                if (random.nextBoolean()) {
                    int swap = a[i - 1]; a[i - 1] = b[i - 1]; b[i - 1] = swap;
                }
            }
            for (int i = 0; i < n; i++) costs[i] = random.nextInt(15);
            Input.Tree tree = new Input.Tree(n, a, b, costs);
            checkCover(tree);
            int[] ones = new int[n]; Arrays.fill(ones, 1);
            checkCover(new Input.Tree(n, a, b, ones));
            equal(bruteDiameter(tree.neighbours()), Outbreak.longestChain(n, tree.neighbours()), "diameter");
        }
        checkCover(new Input.Tree(4, new int[]{0, 1, 2}, new int[]{1, 2, 3},
                new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}));
        checkCover(new Input.Tree(4, new int[]{0, 0, 0}, new int[]{1, 2, 3}, new int[]{10, 1, 1, 1}));
        equal(0, Outbreak.cheapestTests(0, new int[0][], new int[0]).length, "empty tree cover");
        equal(0, Outbreak.longestChain(0, new int[0][]), "empty tree diameter");
        System.out.println("PASS: " + checks + " assertions; exhaustive small strings, 1000 random string cases,");
        System.out.println("400 random trees checked by subset enumeration and all-pairs BFS, and a real hash collision.");
    }

    // An independent oracle: shortest paths in a graph of words, not a DP table.
    private static Map<String, Integer> editGraphDistances(String start, int maxLength) {
        Map<String, Integer> distance = new HashMap<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        distance.put(start, 0); queue.add(start);
        while (!queue.isEmpty()) {
            String s = queue.remove();
            List<String> next = new ArrayList<>();
            for (int i = 0; i < s.length(); i++) {
                next.add(s.substring(0, i) + s.substring(i + 1));
                for (char c : new char[]{'A', 'C'}) next.add(s.substring(0, i) + c + s.substring(i + 1));
            }
            if (s.length() < maxLength) {
                for (int i = 0; i <= s.length(); i++) {
                    for (char c : new char[]{'A', 'C'}) next.add(s.substring(0, i) + c + s.substring(i));
                }
            }
            for (String t : next) {
                if (distance.putIfAbsent(t, distance.get(s) + 1) == null) queue.add(t);
            }
        }
        return distance;
    }

    private static int bruteLcs(String a, String b) {
        int best = 0;
        for (int mask = 0; mask < (1 << a.length()); mask++) {
            int pos = 0, count = 0;
            boolean found = true;
            for (int i = 0; i < a.length(); i++) {
                if ((mask & (1 << i)) == 0) continue;
                while (pos < b.length() && b.charAt(pos) != a.charAt(i)) pos++;
                if (pos == b.length()) { found = false; break; }
                pos++; count++;
            }
            if (found) best = Math.max(best, count);
        }
        return best;
    }

    private static int bruteShared(String a, String b, int k) {
        int total = 0;
        for (int i = 0; i + k <= a.length(); i++) {
            for (int j = 0; j + k <= b.length(); j++) {
                if (same(a, i, b, j, k)) { total++; break; }
            }
        }
        return total;
    }

    private static void checkCover(Input.Tree tree) {
        long best = Long.MAX_VALUE;
        for (int mask = 0; mask < (1 << tree.n()); mask++) {
            boolean covers = true;
            for (int e = 0; e < tree.links(); e++) {
                if ((mask & ((1 << tree.a()[e]) | (1 << tree.b()[e]))) == 0) { covers = false; break; }
            }
            if (!covers) continue;
            long sum = 0;
            for (int v = 0; v < tree.n(); v++) if ((mask & (1 << v)) != 0) sum += tree.cost()[v];
            best = Math.min(best, sum);
        }
        boolean[] selected = Outbreak.cheapestTests(tree.n(), tree.neighbours(), tree.cost());
        long total = 0;
        for (int v = 0; v < tree.n(); v++) if (selected[v]) total += tree.cost()[v];
        for (int e = 0; e < tree.links(); e++) require(selected[tree.a()[e]] || selected[tree.b()[e]], "uncovered edge");
        equal(best, total, "minimum cover cost");
    }

    private static int bruteDiameter(int[][] nb) {
        int best = 0;
        for (int root = 0; root < nb.length; root++) {
            int[] distance = new int[nb.length]; Arrays.fill(distance, -1);
            ArrayDeque<Integer> queue = new ArrayDeque<>(); queue.add(root); distance[root] = 0;
            while (!queue.isEmpty()) {
                int v = queue.remove();
                for (int u : nb[v]) if (distance[u] == -1) {
                    distance[u] = distance[v] + 1; best = Math.max(best, distance[u]); queue.add(u);
                }
            }
        }
        return best;
    }

    private static String dna(Random random, int length) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < length; i++) s.append("ACGT".charAt(random.nextInt(4)));
        return s.toString();
    }

    private static boolean same(String a, int i, String b, int j, int length) {
        for (int k = 0; k < length; k++) if (a.charAt(i + k) != b.charAt(j + k)) return false;
        return true;
    }

    private static long hash(String s) {
        long hash = 0;
        for (int i = 0; i < s.length(); i++) hash = (hash * 257 + s.charAt(i)) % 1_000_000_007;
        return hash;
    }

    private static void equal(long expected, long actual, String label) {
        require(expected == actual, label + ": expected " + expected + ", got " + actual);
    }

    private static void require(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
}
