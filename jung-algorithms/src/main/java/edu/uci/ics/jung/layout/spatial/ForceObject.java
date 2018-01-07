package edu.uci.ics.jung.layout.spatial;

import edu.uci.ics.jung.layout.model.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author Tom Nelson */
public class ForceObject<T> {

  private static final Logger log = LoggerFactory.getLogger(ForceObject.class);

  private double EPSILON = 0.000001D;
  /** location of p */
  public Point p;

  /** force vector */
  protected Point f;

  /** mass */
  protected double mass;

  T element;

  public ForceObject(T element, Point p, double mass) {
    this.element = element;
    this.p = p;
    this.f = Point.ORIGIN;
    this.mass = mass;
  }

  public ForceObject(T element, Point p) {
    this(element, p, 1);
  }

  public ForceObject(T element, double x, double y) {
    this(element, Point.of(x, y), 1);
  }

  public ForceObject(T element, double x, double y, double mass) {
    this.element = element;
    p = Point.of(x, y);
    this.mass = mass;
  }

  public Point getForce() {
    return f;
  }

  public void resetForce() {
    this.f = Point.ORIGIN;
  }

  void addForceFrom(double forceConstant, ForceObject other) {
    double dx = this.p.x - other.p.x;
    double dy = this.p.y - other.p.y;
    log.trace("dx, dy:{},{}", dx, dy);
    double dist = Math.sqrt(dx * dx + dy * dy);
    dist = Math.max(EPSILON, dist);
    log.trace("dist:{}", dist);

    double force = (forceConstant * forceConstant) / dist;
    log.trace("force:{}", force);
    log.trace("force for {} went from p:{}, f:{}...", this.element, this.p, this.f);
    this.f = f.add(force * (dx / dist), force * (dy / dist));
    log.trace("...to p:{}, f:{}", this.p, this.f);
  }

  public ForceObject add(ForceObject other) {
    double totalMass = this.mass + other.mass;
    Point p =
        Point.of(
            (this.p.x * this.mass + other.p.x * other.mass) / totalMass,
            (this.p.y * this.mass + other.p.y * other.mass) / totalMass);

    ForceObject<String> forceObject = new ForceObject<>("force", p, totalMass);
    return forceObject;
  }

  public T getElement() {
    return element;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ForceObject<?> that = (ForceObject<?>) o;

    if (Double.compare(that.mass, mass) != 0) return false;
    if (p != null ? !p.equals(that.p) : that.p != null) return false;
    return element != null ? element.equals(that.element) : that.element == null;
  }

  @Override
  public int hashCode() {
    int result;
    long temp;
    result = p != null ? p.hashCode() : 0;
    temp = Double.doubleToLongBits(mass);
    result = 31 * result + (int) (temp ^ (temp >>> 32));
    result = 31 * result + (element != null ? element.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "ForceObject{"
        + "element="
        + element
        + ", p="
        + p
        + ", mass="
        + mass
        + ", force="
        + f
        + '}';
  }
}
