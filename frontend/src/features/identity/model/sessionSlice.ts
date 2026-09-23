import { createSlice, type PayloadAction } from '@reduxjs/toolkit'

export interface CurrentUser {
  userId: string
  roles: string[]
}

export interface SessionState {
  status: 'unauthenticated' | 'authenticated'
  currentUser: CurrentUser | null
}

const initialState: SessionState = {
  status: 'unauthenticated',
  currentUser: null,
}

const sessionSlice = createSlice({
  name: 'session',
  initialState,
  reducers: {
    sessionAuthenticated(state, action: PayloadAction<CurrentUser>) {
      state.status = 'authenticated'
      state.currentUser = action.payload
    },
    sessionCleared(state) {
      state.status = 'unauthenticated'
      state.currentUser = null
    },
  },
})

export const { sessionAuthenticated, sessionCleared } = sessionSlice.actions
export const sessionReducer = sessionSlice.reducer
