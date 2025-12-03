/**
 * 控制通道：基于 Unix Domain Socket 的通信层
 */
#pragma once

#include <string>
#include <vector>
#include <cstdint>
#include <memory>
#include <mutex>
#include <optional>
#include <sys/socket.h>
#include <sys/un.h>

namespace tinaide {
namespace lsp {

enum class MessageType : uint16_t {
    DATA = 1,
    SHARED_MEMORY_FD = 2,
    CANCEL_REQUEST = 10,
    SHUTDOWN = 11,
    PING = 12,
    PONG = 13,
};

struct MessageHeader {
    uint16_t type;
    uint16_t flags;
    uint32_t payload_size;
    uint64_t request_id;

    MessageHeader() : type(0), flags(0), payload_size(0), request_id(0) {}
    MessageHeader(MessageType t, uint32_t size, uint64_t req_id)
        : type(static_cast<uint16_t>(t)), flags(0),
          payload_size(size), request_id(req_id) {}
};

struct Message {
    MessageHeader header;
    std::vector<uint8_t> payload;
    int fd;

    Message() : fd(-1) {}
    Message(MessageType type, uint64_t request_id,
            const std::vector<uint8_t>& data = {}, int file_descriptor = -1)
        : header(type, data.size(), request_id), payload(data), fd(file_descriptor) {}
};

enum class ChannelError {
    SUCCESS = 0,
    CONNECTION_FAILED,
    SEND_FAILED,
    RECEIVE_FAILED,
    TIMEOUT,
    PEER_CLOSED,
    INVALID_MESSAGE,
    BUFFER_OVERFLOW,
    FD_LIMIT_EXCEEDED,
};

struct ChannelConfig {
    std::string socket_path;
    uint32_t max_message_size;
    uint32_t send_timeout_ms;
    uint32_t recv_timeout_ms;
    bool blocking_mode;

    ChannelConfig()
        : socket_path("/tmp/tinaide_lsp_control.sock"),
          max_message_size(4096),
          send_timeout_ms(5000),
          recv_timeout_ms(5000),
          blocking_mode(true) {}
};

class ControlChannel {
public:
    explicit ControlChannel(const ChannelConfig& config = ChannelConfig());
    ~ControlChannel();

    ControlChannel(const ControlChannel&) = delete;
    ControlChannel& operator=(const ControlChannel&) = delete;
    ControlChannel(ControlChannel&& other) noexcept;
    ControlChannel& operator=(ControlChannel&& other) noexcept;

    ChannelError createServer();
    ChannelError acceptClient(uint32_t timeout_ms = 0);
    ChannelError connect(uint32_t timeout_ms = 5000);
    ChannelError send(const Message& msg);
    ChannelError receive(Message& msg, uint32_t timeout_ms = 0);
    ChannelError sendData(uint64_t request_id, const std::vector<uint8_t>& data);
    ChannelError sendSharedMemoryFd(uint64_t request_id, int fd, uint32_t size);
    void close();
    bool isConnected() const { return socket_fd_ >= 0; }
    std::string getLastError() const { return last_error_; }

private:
    ChannelError sendBytes(const void* data, size_t size);
    ChannelError receiveBytes(void* data, size_t size);
    ChannelError sendFd(int fd);
    ChannelError receiveFd(int& fd);
    bool setSocketTimeout(int socket, uint32_t timeout_ms, bool is_send);

    ChannelConfig config_;
    int socket_fd_;
    int listen_fd_;
    bool is_server_;
    std::mutex send_mutex_;
    std::mutex recv_mutex_;
    std::string last_error_;
};

const char* channelErrorToString(ChannelError error);

}  // namespace lsp
}  // namespace tinaide
