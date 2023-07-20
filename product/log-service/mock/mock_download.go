// Copyright 2020 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

// Code generated by MockGen. DO NOT EDIT.
// Source: ../download/download.go

// Package mock is a generated GoMock package.
package mock

import (
	context "context"
	reflect "reflect"

	gomock "github.com/golang/mock/gomock"
	cache "github.com/harness/harness-core/product/log-service/cache"
	config "github.com/harness/harness-core/product/log-service/config"
	queue "github.com/harness/harness-core/product/log-service/queue"
	store "github.com/harness/harness-core/product/log-service/store"
)

// MockWorker is a mock of Worker interface
type MockWorker struct {
	ctrl     *gomock.Controller
	recorder *MockWorkerMockRecorder
}

// MockWorkerMockRecorder is the mock recorder for MockWorker
type MockWorkerMockRecorder struct {
	mock *MockWorker
}

// NewMockWorker creates a new mock instance
func NewMockWorker(ctrl *gomock.Controller) *MockWorker {
	mock := &MockWorker{ctrl: ctrl}
	mock.recorder = &MockWorkerMockRecorder{mock}
	return mock
}

// EXPECT returns an object that allows the caller to indicate expected use
func (m *MockWorker) EXPECT() *MockWorkerMockRecorder {
	return m.recorder
}

// Execute mocks base method
func (m *MockWorker) Execute(fn func(context.Context, string, queue.Queue, cache.Cache, store.Store, config.Config), q queue.Queue, c cache.Cache, s store.Store, cfg config.Config) {
	m.ctrl.T.Helper()
	m.ctrl.Call(m, "Execute", fn, q, c, s, cfg)
}

// Execute indicates an expected call of Execute
func (mr *MockWorkerMockRecorder) Execute(fn, q, c, s, cfg interface{}) *gomock.Call {
	mr.mock.ctrl.T.Helper()
	return mr.mock.ctrl.RecordCallWithMethodType(mr.mock, "Execute", reflect.TypeOf((*MockWorker)(nil).Execute), fn, q, c, s, cfg)
}