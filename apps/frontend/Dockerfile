FROM node:22-alpine
WORKDIR /app

# 1. Copy dependency manifests
COPY package*.json ./

# 2. Install dependencies
RUN npm ci --prefer-offline || npm install

# 3. Copy application source tree
COPY . .

# 4. Set build arguments and environment variables for Next.js production build
ARG NEXT_PUBLIC_GATEWAY_URL=http://localhost:9738
ENV NEXT_PUBLIC_GATEWAY_URL=$NEXT_PUBLIC_GATEWAY_URL
ENV NODE_ENV=production

# 5. Build application for production
RUN npm run build

ENV HOSTNAME="0.0.0.0"
ENV PORT=3000

EXPOSE 3000

# 6. Start Next.js production server
CMD ["npm", "start"]
