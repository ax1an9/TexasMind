package com.texasholdem.server.stats;

import lombok.Data;

@Data
public class StatEntry {
    private double value = 0.0;
    private int opportunities = 0;
    private int count = 0;

    public void increment() {
        this.count++;
        this.opportunities++;
        this.value = (double) count / opportunities;
    }

    public void addOpportunity() {
        this.opportunities++;
        this.value = opportunities > 0 ? (double) count / opportunities : 0.0;
    }
}
