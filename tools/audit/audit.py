import os
import sys

try:
    import redis
except ImportError:
    print("Error: The 'redis' library is not installed. Please install it using 'pip install redis'.", file=sys.stderr)
    sys.exit(1)

def main():
    # Configuration from environment variables
    host = os.environ.get('REDIS_HOST', 'localhost')
    try:
        port = int(os.environ.get('REDIS_PORT', 6379))
    except ValueError:
        print("Error: REDIS_PORT must be an integer.", file=sys.stderr)
        sys.exit(1)
        
    user = os.environ.get('REDIS_USER')
    password = os.environ.get('REDIS_PASSWORD')

    # Connection to Redis
    try:
        r = redis.Redis(
            host=host,
            port=port,
            username=user,
            password=password,
            decode_responses=True
        )
        # Test connection
        r.ping()
    except redis.exceptions.AuthenticationError:
        print("Error: Authentication failed. Please check REDIS_USER and REDIS_PASSWORD.", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"Error connecting to Redis: {e}", file=sys.stderr)
        sys.exit(1)

    pattern = "resumebot:conversations:*:messages"


    # Iterate over keys matching the pattern
    try:
        # scan_iter matches keys in the database.
        # Note: Iterate order is undefined by Redis, but we process them as we find them.
        for key in r.scan_iter(match=pattern):
            # Key format: resumebot:conversations:{id}:messages
            parts = key.split(':')
            if len(parts) >= 4:
                # conversation_id is at index 2
                conversation_id = parts[2]
            else:
                conversation_id = key

            # Fetch messages from the list
            # We treat the key as a List, based on the application logic (MessageService.java)
            messages = r.lrange(key, 0, -1)
            
            print(f"Conversation: {conversation_id}")
            print()
            for msg in messages:
                print(f"{msg}")
            print("-" * 20)


    except Exception as e:
        print(f"Error processing keys: {e}", file=sys.stderr)
        sys.exit(1)



if __name__ == "__main__":
    main()
