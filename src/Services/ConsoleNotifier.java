package Services;

import Interfaces.INotifier;

public class ConsoleNotifier implements INotifier {
    @Override
    public void notify(String message) {
        System.out.println("[NOTIFY] " + message);
    }
}
