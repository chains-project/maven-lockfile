public class ModuleA {
    public String greet() {
        return new ModuleB().hello();
    }
}
