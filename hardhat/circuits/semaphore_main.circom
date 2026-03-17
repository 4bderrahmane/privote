pragma circom 2.1.5;

include "semaphore.circom";

component main {public [message, scope]} = Semaphore(20);