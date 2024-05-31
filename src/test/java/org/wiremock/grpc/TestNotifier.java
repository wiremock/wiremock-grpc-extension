package org.wiremock.grpc;

import com.github.tomakehurst.wiremock.common.Notifier;

import java.util.ArrayList;
import java.util.List;

public class TestNotifier implements Notifier {

    public List<String> infoMessages = new ArrayList<>();
    public List<String> errorMessages = new ArrayList<>();
    public List<Throwable> throwables = new ArrayList<>();

    public int numberOfMessages = 0;

    public void clear() {
        infoMessages.clear();
        errorMessages.clear();
        throwables.clear();
        numberOfMessages = 0;
    }

    @Override
    public void info(String message) {
        infoMessages.add(message);
        System.out.println(++numberOfMessages + "\t" + message);
    }

    @Override
    public void error(String message) {
        errorMessages.add(message);
        System.err.println(++numberOfMessages + "\t" + message);
    }

    @Override
    public void error(String message, Throwable t) {
        errorMessages.add(message);
        throwables.add(t);
        System.err.println(++numberOfMessages + "\t" + message);
    }
}
