import java.util.*;

/**
 * Created by Jonathan on 7/31/16.
 */
public class Trie {

    private HashMap<Character, TrieNode> myStartingLetters;

    public Trie() {
        myStartingLetters = new HashMap<>();
    }

    public void addLocation(String location, GraphNode info) {
        //lower case location string with spaces removed
        String clean = location.replaceAll("[^a-zA-Z ]", "").toLowerCase();

        HashMap<Character, TrieNode> currMap = myStartingLetters;

        for (int i = 0; i < clean.length(); i++) {
            TrieNode next = currMap.get(clean.charAt(i));

            if (next == null) {
                currMap.put(clean.charAt(i), new TrieNode());
                next = currMap.get(clean.charAt(i));
            }

            currMap = next.myNextLetters;

            if (i == clean.length() - 1) {
                next.setLocation(location, info);
            }
        }
    }

    public List<String> lookupPrefix(String prefix) {

        HashMap<Character, TrieNode> currMap = myStartingLetters;
        prefix = prefix.replaceAll("[^a-zA-Z ]", "").toLowerCase();

        for (int i = 0; i < prefix.length() - 1; i++) {
            TrieNode next = currMap.get(prefix.charAt(i));
            if (next == null) return null;
            currMap = next.myNextLetters;
        }

        return currMap.get(prefix.charAt(prefix.length() - 1)).getWords();

    }

    public HashSet<GraphNode> lookup(String location) {

        HashMap<Character, TrieNode> currMap = myStartingLetters;
        location = location.replaceAll("[^a-zA-Z ]", "").toLowerCase();

        for (int i = 0; i < location.length() - 1; i++) {
            TrieNode next = currMap.get(location.charAt(i));
            if (next == null) return null;
            currMap = next.myNextLetters;
        }

        return currMap.get(location.charAt(location.length() - 1)).getLocationInfo();

    }

    private class TrieNode {
        // Maps to next characters
        private HashMap<Character, TrieNode> myNextLetters;
        // Leave this null if this TrieNode is not the end of a complete word.
        private String locationName;
        // Stores corresponding GraphNodes + attributes of the location/point of interest)
        private HashSet<GraphNode> locationInfo;


        private TrieNode() {
            this.myNextLetters = new HashMap<>();
            this.locationInfo = new HashSet<>();

        }

        public List<String> getWords() {
            ArrayList<String> words = new ArrayList<>();
            getTerminalNodes(this, words);
            return words;
        }

        private void getTerminalNodes(TrieNode curr, List<String> terminal) {
            if (curr.getLocationName() != null) terminal.add(curr.getLocationName());
            for (Character e: curr.myNextLetters.keySet()) {
                TrieNode nextTrieNode = curr.myNextLetters.get(e);
                if (nextTrieNode != null) getTerminalNodes(nextTrieNode, terminal);
            }
        }

        public void setLocation(String name, GraphNode info) {
            locationName = name;
            locationInfo.add(info);
        }

        public String getLocationName() {
            return locationName;
        }

        public HashSet<GraphNode> getLocationInfo() {
            return locationInfo;
        }
    }

    /*public static void main (String[] args) {
        Trie location = new Trie();
        location.addLocation("Sushi California");
        location.addLocation("Sushi Sho");
        location.addLocation("Sushi Secrets");
        location.addLocation("Sushi Solano");
        location.addLocation("Sushi Ko");
        System.out.println(location.lookupPrefix("Sushi Cal"));
    }*/

}
