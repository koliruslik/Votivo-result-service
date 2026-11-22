package com.votivo.result.domain;

/** Whether a poll still takes votes. Its result is final once it is no longer {@link #ACTIVE}. */
public enum PollState { ACTIVE, CLOSED, BLOCKED }
