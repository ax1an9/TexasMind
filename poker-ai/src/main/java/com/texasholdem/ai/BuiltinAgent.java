package com.texasholdem.ai;

import com.texasholdem.core.model.Action;
import com.texasholdem.core.model.GameState;
import com.texasholdem.core.model.PlayerState;

public interface BuiltinAgent {
    Action decide(GameState state, PlayerState self);
}