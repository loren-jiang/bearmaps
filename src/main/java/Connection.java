import java.util.Objects;

/**
 * Created by Loren on 7/30/2016.
 */

public class Connection implements Comparable<Double> {

    private Long from;
    private Long to;
    private Double distance;

    public Connection(Long from, Long to, Double distance) {
        this.from = from;
        this.to = to;
        this.distance = distance;
    }

    public Double getDistance() {
        return distance;
    }

    public Long to() {
        return to;
    }

    public Long from() {
        return from;
    }

    public String toString() {
        return "(From: " + from + "; To: " + to + "; Distance = " + ")";
    }

    @Override
    public int compareTo(Double otherDistance) {
        return this.distance.compareTo(otherDistance);
    }

    @Override
    public boolean equals(Object c) {
        if (!(c instanceof Connection)) {
            return false;
        }
        return Objects.equals(from, ((Connection) c).from)
                && Objects.equals(to, ((Connection) c).from);
    }

    @Override
    public int hashCode() {
        return to.hashCode() + from.hashCode();
    }
}

