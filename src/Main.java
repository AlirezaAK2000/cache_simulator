import java.io.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        Cache cache = CacheBuilder.build(scanner);
        cache.beginTrace(scanner);
        cache.printInfo();
    }
}

abstract class Cache {
    protected int block_size;
    protected boolean unified;
    protected int associativity;
    protected int[] cache_size;
    protected int[] how_many_set;
    protected String path;
    protected String write_to_memory_policy;
    protected LRUStructure[][] cache_structure;
    protected int word_count;
    protected HashMap<String, Integer> hit;
    protected HashMap<String, Integer> access;
    protected long word_demand = 0;
    protected long word_copied_back = 0;
    private int[] replaces;
    public Cache(int block_size, boolean unified, int associativity, int[] cache_size, String path, String write_to_memory_policy) {
        this.block_size = block_size;
        this.unified = unified;
        this.associativity = associativity;
        this.cache_size = cache_size;
        this.path = path;
        this.write_to_memory_policy = write_to_memory_policy;
        this.word_count = block_size / 4;
        this.hit = new HashMap<>();
        this.access = new HashMap<>();
        this.hit.put("d", 0);
        this.access.put("d", 0);
        this.hit.put("i", 0);
        this.access.put("i", 0);
        this.replaces = new int[2];
        replaces[0] = 0;
        replaces[1] = 0;
        this.how_many_set = new int[cache_size.length];
        this.cache_structure = new LRUStructure[cache_size.length][];

        for (int i = 0; i < cache_size.length; i++) {
            how_many_set[i] = cache_size[i] / (block_size * associativity);
            LRUStructure[] array = new LRUStructure[how_many_set[i]];
            cache_structure[i] = array;
            for (int j = 0; j < how_many_set[i]; j++) {
                array[j] = new LRUStructure(associativity, word_count);
            }
        }

    }
    private void doAccess(String[] info) {
        switch (info[0]) {
            case "0":
                access.put("d", access.get("d") + 1);
                load(info[1] , "d");
                break;
            case "1":
                access.put("d", access.get("d") + 1);
                storeData(info[1]);
                break;
            case "2":
                access.put("i", access.get("i") + 1);
                load(info[1] , "i");
                break;
        }
    }
    public void beginTrace(Scanner scanner) {
        ArrayList<String[]> data = new ArrayList<>();
        while (true) {
            String str = scanner.nextLine();
            if (str.length() == 0) {
                break;
            }
            String[] info = str.split(" ");
            data.add(info);
        }
        for (String[] request : data) {
            if (request.length >= 2)
                doAccess(request);
        }
    }
    private void load(String address , String cache_type) {
        boolean hit = checkHit(address, "r", "wa", cache_type);
        if (hit) {
            this.hit.put(cache_type, this.hit.get("d") + 1);
        } else
            this.word_demand += word_count;
    }
    public abstract void storeData(String address);
    public boolean checkHit(String address, String action, String policy, String cashe_type) {
        long add = Integer.parseInt(address, 16);
        int set = cashe_type.equals("i") && !unified ? 1 : 0;
        int index = (int) ((add / block_size) % how_many_set[set]);
        long tag = add / (block_size * how_many_set[set]);
        address = Long.toHexString(tag);
        if (!cashe_type.equals("i") || cache_structure.length == 1)
            if (cashe_type.equals("i"))
                return cache_structure[0][index].access(address, action, policy, "i", "d");
            else
                return cache_structure[0][index].access(address, action, policy, "d", "i");
        return cache_structure[1][index].access(address, action, policy, "d", "i");
    }
    public void printInfo() {
        setReplaces();
        String architecture = null;
        String sizes = null;
        if (unified) {
            architecture = "Unified I- D-cache";
            sizes = "Size: " + cache_size[0];
        } else {
            architecture = "Split I- D-cache";
            sizes = "I-cache size: " + cache_size[1] + "\n";
            sizes += "D-cache size: " + cache_size[0];
        }
        System.out.println("***CACHE SETTINGS***");
        System.out.println(architecture);
        System.out.println(sizes);
        System.out.println("Associativity: " + associativity);
        System.out.println("Block size: " + block_size);
        String write_policy = this instanceof WTCache ? "WRITE THROUGH" : "WRITE BACK";
        System.out.println("Write policy: " + write_policy);
        String allocation_policy = write_to_memory_policy.equals("wa") ? "WRITE ALLOCATE" : "WRITE NO ALLOCATE";
        System.out.println("Allocation policy: " + allocation_policy + "\n");
        System.out.println("***CACHE STATISTICS***");
        String cache_instructor = "INSTRUCTIONS";
        int i_access = access.get("i");
        String cache_data = "DATA";
        int d_access = access.get("d");
        int d_misses = d_access - hit.get("d");
        if (i_access != 0) {
            int i_misses = i_access - hit.get("i");
            float i_miss_rate = (float) (i_misses) / i_access;
            printStatistics(cache_instructor, i_access, i_misses, i_miss_rate, replaces[1]);
        } else {
            printStatistics(cache_instructor, 0, 0, 0.0f, 0);
        }
        float d_miss_rate = (float) (d_misses) / d_access;
        printStatistics(cache_data, d_access, d_misses, d_miss_rate, replaces[0]);
        System.out.println("TRAFFIC (in words)");
        System.out.println("demand fetch: " + word_demand);
        System.out.println("copies back: " + word_copied_back);    }
    private void setReplaces() {
        if (!unified) {
            for (int i = 0; i < cache_size.length; i++) {
                for (int j = 0; j < cache_structure[i].length; j++)
                    replaces[i] += cache_structure[i][j].getReplaces();
            }
        } else {
            for (int i = 0; i < cache_structure[0].length; i++) {
                replaces[0] += cache_structure[0][i].getReplaces();
                replaces[1] += cache_structure[0][i].getReplaces_unified();
            }
        }
    }
    private void printStatistics(String cache_type, int access, int misses, float miss_rate, int replace) {
        DecimalFormat df = new DecimalFormat("#.####");
        df.setMinimumFractionDigits(4);
        System.out.println(cache_type);
        System.out.println("accesses: " + access);
        System.out.println("misses: " + misses);
        float hit_rate = access != 0 ? 1.0f - miss_rate : 0.000f;
        if (access != 0)
            System.out.println("miss rate: " + df.format(miss_rate) + " (hit rate " + df.format(hit_rate) + ")");
        else
            System.out.println("miss rate: " + "0.0000" + " (hit rate " + "0.0000" + ")");

        System.out.println("replace: " + replace);
    }
}
class WTCache extends Cache {
    public WTCache(int block_size, boolean unified, int associativity, int[] cache_size, String path, String write_to_memory_policy) {
        super(block_size, unified, associativity, cache_size, path, write_to_memory_policy);
    }
    @Override
    public void storeData(String address) {
        boolean hit = checkHit(address, "w", write_to_memory_policy, "d");
        if (hit) {
            this.hit.put("d", this.hit.get("d") + 1);
        } else if (write_to_memory_policy.equals("wa")) {
            this.word_demand += this.word_count;
        }
        this.word_copied_back++;
    }
}
class WBCache extends Cache {
    public WBCache(int block_size, boolean unified, int associativity, int[] cache_size, String path, String write_to_memory_policy) {
        super(block_size, unified, associativity, cache_size, path, write_to_memory_policy);
    }
    @Override
    public void storeData(String address) {
        boolean hit = checkHit(address, "w", write_to_memory_policy, "d");
        if (hit) {
            this.hit.put("d", this.hit.get("d") + 1);
        } else if (!write_to_memory_policy.equals("nw")) {
            this.word_demand += word_count;
        }
    }
    @Override
    public void printInfo() {
        countDirtyReplacedBlocks();
        super.printInfo();
    }
    public void countDirtyReplacedBlocks() {
        for (LRUStructure lru : cache_structure[0]) {
            word_copied_back += lru.countDirty();
        }
    }
}
class LRUStructure {
    private CacheBlock head;
    private CacheBlock tail;
    private HashMap<String, CacheBlock> comparator;
    private int max_size;
    private int word_copied = 0;
    private int block_word_count;
    private int replaces = 0;
    private int replaces_unified = 0;
    public LRUStructure(int associativity, int block_word_count) {
        this.max_size = associativity;
        this.comparator = new HashMap<>();
        this.block_word_count = block_word_count;
    }
    public boolean access(String address, String action, String policy, String type, String dataType) {
        boolean hit = comparator.containsKey(address);
        addToSet(address, hit, action, policy, type);
        return hit;
    }
    private void addToSet(String address, boolean hit, String action, String policy, String type) {
        if (hit) {
            replacePolicy(comparator.get(address), action);
        } else if (policy.equals("wa") || action.equals("r")) {
            addBlock(new CacheBlock(address), type, action);
        } else if (policy.equals("nw") && action.equals("w")) {
            word_copied++;
        }
    }
    private void replacePolicy(CacheBlock block, String action) {
        if (action.equals("w")) {
            block.setDirty(true);
            return;
        }
        if (block.getAddress().equals(head.getAddress())) {
            return;
        }
        if (!block.getAddress().equals(tail.getAddress())) {
            CacheBlock left = block.getLeft();
            CacheBlock right = block.getRight();
            left.setRight(right);
            right.setLeft(left);
        } else {
            CacheBlock left = block.getLeft();
            left.setRight(null);
            tail = left;
        }
        addToBegging(block);
    }
    private void addBlock(CacheBlock block, String type, String action) {
        if (action.equals("w"))
            block.setDirty(true);
        if (tail == null || head == null) {
            head = block;
            tail = block;
            comparator.put(block.getAddress(), block);
            return;
        }
        if (max_size == 1) {
            if (head.isDirty())
                word_copied += block_word_count;
            comparator.remove(head.getAddress());
            head = block;
            tail = block;
            comparator.put(block.getAddress(), block);
            if (type.equals("i"))
                replaces_unified++;
            else
                ++replaces;
            return;
        }
        if (comparator.size() == max_size) {
            if (tail.isDirty()) {
                word_copied += block_word_count;
            }
            comparator.remove(tail.getAddress());
            tail = tail.getLeft();
            tail.setRight(null);
            if (type.equals("i"))
                replaces_unified++;
            else
                ++replaces;
        }
        addToBegging(block);
        comparator.put(block.getAddress(), block);
    }
    private void addToBegging(CacheBlock block) {
        block.setLeft(null);
        block.setRight(head);
        head.setLeft(block);
        head = block;
    }
    public int countDirty() {
        for (String add : comparator.keySet())
            if (comparator.get(add).isDirty())
                word_copied += block_word_count;
        return word_copied;
    }
    public int getReplaces() {
        return replaces;
    }
    public int getReplaces_unified() {
        return replaces_unified;
    }
}
class CacheBlock {
    private String address;
    private CacheBlock right;
    private CacheBlock left;
    private boolean dirty = false;
    public boolean isDirty() {
        return dirty;
    }
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
    public CacheBlock(String address) {
        this.address = address;
    }
    public CacheBlock getRight() {
        return right;
    }
    public String getAddress() {
        return address;
    }
    public void setRight(CacheBlock right) {
        this.right = right;
    }
    public CacheBlock getLeft() {
        return left;
    }
    public void setLeft(CacheBlock left) {
        this.left = left;
    }
    @Override
    public String toString() {
        return address;
    }
}
class CacheBuilder {
    public static Cache build(Scanner scanner) {
        String[] info = scanner.nextLine().trim().split(" - ");
        String[] sizes = scanner.nextLine().trim().split(" - ");
        return buildCache(info, sizes, null);
    }
    private static Cache buildCache(String[] info, String[] sizes, String path) {
        int block_size = Integer.parseInt(info[0]);
        boolean unified = info[1].equals("0");
        int associativity = Integer.parseInt((info[2]));
        String write_policy = info[3];
        String write_to_memory_policy = info[4];
        int[] cache_sizes = new int[sizes.length];
        int j = 0;
        for (int i = sizes.length - 1; i >= 0; i--)
            cache_sizes[j++] = Integer.parseInt(sizes[i]);
        switch (write_policy) {
            case "wb":
                Cache wb_cache = new WBCache(block_size, unified, associativity, cache_sizes, path, write_to_memory_policy);
                return wb_cache;
            default:
                Cache wt_cache = new WTCache(block_size, unified, associativity, cache_sizes, path, write_to_memory_policy);
                return wt_cache;
        }
    }
}